package net.coremc.skyblock.crates;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Weighted loot-table opener for normal (key) crates.
 *
 * <p>Each key has a pool under {@code crates.keys.<id>.pool} — a list of
 * {@code reward: weight} entries (reward id from {@code crates.rewards}). The opener
 * selects {@code crates.keys.<id>.rewards} entries (default 1) using weighted random
 * selection. Weights are fully configurable; nothing is hard-coded.</p>
 */
public final class CrateOpener {

    private final JavaPlugin plugin;
    private final RewardResolver rewards;

    public CrateOpener(final JavaPlugin plugin, final RewardResolver rewards) {
        this.plugin = plugin;
        this.rewards = rewards;
    }

    /** Open a key for a player: grant the configured number of weighted rewards. */
    public void open(final Player p, final KeyId id) {
        final var cfg = plugin.getConfig();
        final String section = "crates.keys." + id.id();
        final int count = Math.max(1, cfg.getInt(section + ".rewards", 1));
        final List<Weighted> pool = loadPool(poolSection(id));
        if (pool.isEmpty()) {
            plugin.getLogger().warning("[crates] empty pool for key: " + id.id());
            return;
        }
        final List<String> won = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final String pick = pick(pool);
            if (pick != null) {
                rewards.apply(p, pick);
                won.add(pick);
            }
        }
        announce(p, id, won);
    }

    /** Pick a single weighted reward id, or null if the pool is empty. */
    public String pickOne(final KeyId id) {
        final var cfg = plugin.getConfig();
        final List<Weighted> pool = loadPool(poolSection(id));
        return pool.isEmpty() ? null : pick(pool);
    }

    /** Resolve the loot-table section for a key. Event/Monthly keys read their active
     *  configurable table so new events/months can replace rewards without a code change. */
    private org.bukkit.configuration.ConfigurationSection poolSection(final KeyId id) {
        final var cfg = plugin.getConfig();
        if (id == KeyId.EVENT) {
            final String active = cfg.getString("crates.keys.event.active", "summer");
            final var sec = cfg.getConfigurationSection("crates.events." + active + ".items");
            if (sec != null) return sec;
        }
        if (id == KeyId.MONTHLY) {
            final String active = cfg.getString("crates.keys.monthly.active", "january");
            final var sec = cfg.getConfigurationSection("crates.monthly." + active + ".items");
            if (sec != null) return sec;
        }
        return cfg.getConfigurationSection("crates.keys." + id.id() + ".pool");
    }

    private List<Weighted> loadPool(final ConfigurationSection pool) {
        final List<Weighted> out = new ArrayList<>();
        if (pool == null) return out;
        for (final String key : pool.getKeys(false)) {
            final int weight = pool.getInt(key, 1);
            if (weight > 0) out.add(new Weighted(key, weight));
        }
        return out;
    }

    private String pick(final List<Weighted> pool) {
        long total = 0;
        for (final Weighted w : pool) total += w.weight;
        if (total <= 0) return null;
        long r = ThreadLocalRandom.current().nextLong(total);
        for (final Weighted w : pool) {
            r -= w.weight;
            if (r < 0) return w.id;
        }
        return pool.get(pool.size() - 1).id;
    }

    private void announce(final Player p, final KeyId id, final List<String> won) {
        final var cfg = plugin.getConfig();
        final var msg = net.coremc.foundation.CoreFoundation.getInstance().messages();

        // Optional lightweight "opened a key" broadcast (uses the main message pipeline).
        if (cfg.getBoolean("crates.broadcast-opening", false)) {
            msg.broadcast(plugin, "<prefix> <" + keyColour(id) + ">" + p.getName()
                    + "</" + keyColour(id) + "> opened a <" + keyColour(id) + ">"
                    + id.defaultName() + "</" + keyColour(id) + ">!");
        }

        // Genuine good-reward chat: broadcast each reward flagged `announce: true`.
        // Uses the same Messages pipeline as the rest of CoreMC (prefix + bold + parse).
        for (final String rewardId : won) {
            if (!cfg.getBoolean("crates.rewards." + rewardId + ".announce", false)
                    && !cfg.getBoolean("crates.lootbox-rewards." + rewardId + ".announce", false)) {
                continue;
            }
            final String display = cfg.getString("crates.rewards." + rewardId + ".display",
                    cfg.getString("crates.lootbox-rewards." + rewardId + ".display", rewardId));
            final String colour = keyColour(id);
            msg.broadcast(plugin, "<prefix> <" + colour + ">" + p.getName() + "</" + colour
                    + "> won <" + colour + ">" + display + "</" + colour + "> from a <" + colour
                    + ">" + id.defaultName() + "</" + colour + ">!");
        }
    }

    private String keyColour(final KeyId id) {
        return plugin.getConfig().getString("crates.keys." + id.id() + ".colour", "white");
    }

    private static final class Weighted {
        final String id;
        final int weight;
        Weighted(final String id, final int weight) {
            this.id = id;
            this.weight = weight;
        }
    }
}
