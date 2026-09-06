package net.coremc.skyblock.missions;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.util.CreditsBridge;
import net.coremc.foundation.util.Economy;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.storage.Island;
import net.coremc.skyblock.crates.KeyId;
import net.coremc.skyblock.crates.KeyItem;
import net.coremc.skyblock.progression.ProgressionModule;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Loads missions from missions.yml, tracks per-player progress, and pays out
 * rewards through the EXISTING CoreMC systems (Vault money / Sky Tokens via
 * {@link Economy}, {@link CreditsBridge} for Credits, Sky Token island XP via the
 * progression API, and crate keys / items via the crates module). No duplicate
 * currencies or progression systems are introduced.
 */
public final class MissionManager {

    private final JavaPlugin plugin;
    private final Map<String, Mission> missions = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Long>> progress = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.Set<String>> claimed = new ConcurrentHashMap<>();
    private final File progressFile;
    private final YamlConfiguration progressData;

    private KeyItem keyItem;

    public MissionManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.progressFile = new File(plugin.getDataFolder(), "missions_progress.yml");
        this.progressData = YamlConfiguration.loadConfiguration(progressFile);
    }

    public void init() {
        // Crate key builder (for key rewards).
        try {
            this.keyItem = new KeyItem(plugin);
        } catch (final Throwable t) {
            plugin.getLogger().warning("Missions: crate KeyItem unavailable: " + t.getMessage());
        }
        loadMissions();
        loadProgress();
        plugin.getLogger().info("Missions loaded: " + missions.size() + " missions.");
    }

    private void loadMissions() {
        final ConfigurationSection root = plugin.getConfig().getConfigurationSection("missions");
        if (root == null) {
            plugin.getLogger().warning("No 'missions' section found in config.yml — missions disabled.");
            return;
        }
        for (final String id : root.getKeys(false)) {
            final ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            missions.put(id, new Mission(id, s));
        }
    }

    @SuppressWarnings("unchecked")
    private void loadProgress() {
        final ConfigurationSection players = progressData.getConfigurationSection("players");
        if (players == null) return;
        for (final String k : players.getKeys(false)) {
            try {
                final UUID uuid = UUID.fromString(k);
                final ConfigurationSection p = players.getConfigurationSection(k);
                final Map<String, Long> prog = new java.util.HashMap<>();
                final java.util.Set<String> done = java.util.concurrent.ConcurrentHashMap.newKeySet();
                if (p != null) {
                    final ConfigurationSection pr = p.getConfigurationSection("progress");
                    if (pr != null) {
                        for (final String mk : pr.getKeys(false)) {
                            prog.put(mk, pr.getLong(mk));
                        }
                    }
                    final List<String> claimedList = p.getStringList("claimed");
                    done.addAll(claimedList);
                }
                progress.put(uuid, prog);
                claimed.put(uuid, done);
            } catch (final IllegalArgumentException ignored) {}
        }
    }

    public List<Mission> all() {
        return new ArrayList<>(missions.values());
    }

    public Mission byId(final String id) {
        return missions.get(id);
    }

    // ---- progress ----

    public long getProgress(final UUID uuid, final String missionId) {
        final Map<String, Long> p = progress.get(uuid);
        return p == null ? 0 : p.getOrDefault(missionId, 0L);
    }

    /** Increment a player's progress for a mission by {@code amount} (no-op if already claimed & not repeatable). */
    public void addProgress(final UUID uuid, final String missionId, final long amount) {
        final Mission m = missions.get(missionId);
        if (m == null) return;
        if (isClaimed(uuid, missionId) && !m.repeatable()) return;
        final Map<String, Long> p = progress.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        final long next = p.getOrDefault(missionId, 0L) + amount;
        p.put(missionId, next);
        saveProgress(uuid, missionId, next);
    }

    /** Set a player's progress to an absolute value (used for live-synced categories like island XP). */
    public void setProgress(final UUID uuid, final String missionId, final long value) {
        final Mission m = missions.get(missionId);
        if (m == null) return;
        if (isClaimed(uuid, missionId) && !m.repeatable()) return;
        final Map<String, Long> p = progress.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        p.put(missionId, value);
        saveProgress(uuid, missionId, value);
    }

    public boolean isComplete(final UUID uuid, final Mission m) {
        return getProgress(uuid, m.id()) >= m.requirement();
    }

    public boolean isClaimed(final UUID uuid, final String missionId) {
        final java.util.Set<String> c = claimed.get(uuid);
        return c != null && c.contains(missionId);
    }

    private void saveProgress(final UUID uuid, final String missionId, final long value) {
        progressData.set("players." + uuid + ".progress." + missionId, value);
        save();
    }

    private void saveClaimed(final UUID uuid) {
        final java.util.Set<String> c = claimed.get(uuid);
        progressData.set("players." + uuid + ".claimed", c == null ? new ArrayList<>() : new ArrayList<>(c));
        save();
    }

    private void save() {
        try {
            progressData.save(progressFile);
        } catch (final java.io.IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save missions progress: " + e.getMessage());
        }
    }

    /** Claim a completed mission, paying its rewards. Returns true on success. */
    public boolean claim(final Player player, final String missionId) {
        final Mission m = missions.get(missionId);
        if (m == null) return false;
        final UUID uuid = player.getUniqueId();
        if (!isComplete(uuid, m)) {
            CoreFoundation.getInstance().messages().sendRaw(player,
                    CoreFoundation.getInstance().messages().getPrefix() + " <red>That mission is not complete yet.");
            return false;
        }
        if (isClaimed(uuid, missionId) && !m.repeatable()) {
            CoreFoundation.getInstance().messages().sendRaw(player,
                    CoreFoundation.getInstance().messages().getPrefix() + " <red>You have already claimed that mission.");
            return false;
        }
        payRewards(player, m);
        if (m.repeatable()) {
            // Reset progress so it can be earned again.
            final Map<String, Long> p = progress.get(uuid);
            if (p != null) p.put(missionId, 0L);
            saveProgress(uuid, missionId, 0);
        } else {
            claimed.computeIfAbsent(uuid, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(missionId);
            saveClaimed(uuid);
        }
        CoreFoundation.getInstance().messages().sendRaw(player,
                CoreFoundation.getInstance().messages().getPrefix() + " <green>Mission complete! Rewards claimed.");
        return true;
    }

    /** Apply a mission's rewards through the existing CoreMC systems. */
    private void payRewards(final Player player, final Mission m) {
        final Mission.Reward r = m.reward();
        if (r.isEmpty()) return;
        final UUID uuid = player.getUniqueId();

        // Money (Vault, or Sky Tokens fallback) via the shared Economy helper.
        if (r.money > 0) {
            final Economy econ = new Economy(plugin);
            econ.addMoney(uuid, r.money);
        }
        // Sky Tokens.
        if (r.tokens > 0) {
            final ProgressionModule prog = CoreMC.getInstance().progression();
            if (prog != null) {
                prog.api().awardTokens(uuid, r.tokens);
            }
        }
        // Island XP (requires an island).
        if (r.islandXp > 0) {
            final ProgressionModule prog = CoreMC.getInstance().progression();
            final IslandApi api = CoreMC.getInstance().islands().api();
            final Island island = api.getIsland(uuid);
            if (prog != null && island != null) {
                prog.api().awardIslandXp(island.getId(), r.islandXp);
            }
        }
        // Credits (CoreMC-Store bridge, if present).
        if (r.credits > 0) {
            final CreditsBridge credits = new CreditsBridge(plugin);
            if (credits.available()) {
                credits.getCredits(uuid); // ensure bridge initialised
                try {
                    final var store = org.bukkit.Bukkit.getPluginManager().getPlugin("CoreMC-Store");
                    if (store != null) {
                        final Object svc = store.getClass().getMethod("credits").invoke(store);
                        svc.getClass().getMethod("add", UUID.class, long.class, String.class, String.class)
                                .invoke(svc, uuid, r.credits, "Missions", "mission:" + m.id());
                    }
                } catch (final Throwable t) {
                    plugin.getLogger().warning("Missions: could not grant credits: " + t.getMessage());
                }
            }
        }
        // Keys.
        grantKeys(player, KeyId.SKY, r.skyKeys);
        grantKeys(player, KeyId.RIVER, r.riverKeys);
        grantKeys(player, KeyId.CRIMSON, r.crimsonKeys);
        grantKeys(player, KeyId.VOTE, r.voteKeys);
        grantKeys(player, KeyId.MONTHLY, r.monthlyKeys);
        grantKeys(player, KeyId.EVENT, r.eventKeys);
        grantKeys(player, KeyId.BOOST, r.boostKeys);
        // Items ("MATERIAL:amount[:data]" — data optional, ignored for simplicity).
        for (final String entry : r.items) {
            try {
                final String[] parts = entry.split(":");
                final Material mat = Material.valueOf(parts[0].trim().toUpperCase(java.util.Locale.ROOT));
                final int amount = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
                player.getInventory().addItem(new ItemStack(mat, amount));
            } catch (final IllegalArgumentException e) {
                plugin.getLogger().warning("Missions: bad item entry '" + entry + "'");
            }
        }
    }

    private void grantKeys(final Player player, final KeyId id, final int n) {
        if (n <= 0 || keyItem == null) return;
        player.getInventory().addItem(keyItem.build(id, n));
    }

    public void shutdown() {
        save();
    }
}
