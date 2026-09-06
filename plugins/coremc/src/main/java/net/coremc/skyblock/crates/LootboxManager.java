package net.coremc.skyblock.crates;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.skyblock.crates.config.LootboxConfig;
import net.coremc.skyblock.crates.config.LootboxConfig.AnimationConfig;
import net.coremc.skyblock.crates.config.LootboxConfig.Reward;
import net.coremc.skyblock.crates.rarity.Rarity;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class LootboxManager {

    private final JavaPlugin plugin;
    private final RewardResolver rewards;
    private final Map<String, LootboxConfig> boxes = new LinkedHashMap<>();
    private final Map<UUID, ActiveOpening> activeOpenings = new HashMap<>();

    public LootboxManager(final JavaPlugin plugin, final RewardResolver rewards) {
        this.plugin = plugin;
        this.rewards = rewards;
    }

    /** Load all lootbox configs from lootboxes/*.yml. */
    public void load() {
        boxes.clear();
        final File dir = new File(plugin.getDataFolder(), "lootboxes");
        if (!dir.exists()) {
            dir.mkdirs();
            saveDefaultConfigs();
        }
        final File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (final File f : files) {
            final String id = f.getName().substring(0, f.getName().length() - 4);
            try {
                boxes.put(id, new LootboxConfig(plugin, id));
                plugin.getLogger().info("[lootbox] Loaded: " + id);
            } catch (final Exception e) {
                plugin.getLogger().warning("[lootbox] Failed to load " + id + ": " + e.getMessage());
            }
        }
    }

    private void saveDefaultConfigs() {
        for (final String name : new String[]{"beta", "sotw", "summer"}) {
            final File f = new File(plugin.getDataFolder(), "lootboxes/" + name + ".yml");
            if (!f.exists()) {
                plugin.saveResource("lootboxes/" + name + ".yml", false);
            }
        }
    }

    /** Reload all lootbox configs. */
    public void reload() {
        load();
    }

    /** Accessor for the RewardResolver (used by preview/animation GUIs). */
    public RewardResolver rewards() { return rewards; }

    public Set<String> ids() { return boxes.keySet(); }

    public boolean hasLootboxes() { return !boxes.isEmpty(); }

    public @Nullable LootboxConfig get(final String id) { return boxes.get(id); }

    /** Get the lootbox config from a held item, or null. */
    public @Nullable LootboxConfig get(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        final ItemMeta meta = item.getItemMeta();
        final NamespacedKey nsd = new NamespacedKey(plugin, "coremc.lootbox");
        final String tag = meta.getPersistentDataContainer().get(nsd, org.bukkit.persistence.PersistentDataType.STRING);
        if (tag == null) return null;
        return boxes.get(tag);
    }

    /** Check if an item is a lootbox item. */
    public boolean isLootboxItem(final ItemStack item) {
        return get(item) != null;
    }

    /** Build a lootbox item stack. */
    public ItemStack build(final String boxId, final int amount) {
        final LootboxConfig cfg = boxes.get(boxId);
        if (cfg == null) return new ItemStack(Material.BARRIER);
        return cfg.buildItem(Math.max(1, amount));
    }

    /** Open the lootbox lobby GUI showing all available lootboxes. */
    public void openLobby(final Player p) {
        final LootboxSelectionGui selection = new LootboxSelectionGui(plugin, this);
        selection.open(p);
    }

    /** Open the selection GUI (alias for openLobby). */
    public void openSelectionGui(final Player p) {
        openLobby(p);
    }

    /** Open the preview GUI for a lootbox by id. */
    public void openPreview(final Player p, final String boxId) {
        final LootboxConfig cfg = boxes.get(boxId);
        if (cfg == null) { openLobby(p); return; }
        new LootboxPreviewGui(plugin, this, cfg, rewards).open(p);
    }

    /**
     * Open the preview GUI for a lootbox config directly.
     * Shows all rewards grouped by rarity with chance percentages.
     */
    public void openPreview(final Player p, final LootboxConfig cfg) {
        new LootboxPreviewGui(plugin, this, cfg, rewards).open(p);
    }

    /**
     * Open a lootbox by id — for use from commands or placing the block.
     * Consumes the lootbox from inventory (if applicable) and runs the GUI animation.
     */
    public void openFromPlace(final Player p, final LootboxConfig cfg) {
        openById(p, cfg);
    }

    /**
     * Open a lootbox by boxId. Checks anti-spam, selects reward server-side, starts animation.
     */
    public void openById(final Player p, final LootboxConfig cfg) {
        if (cfg == null) return;
        // Anti-spam: prevent opening while another lootbox is being opened.
        if (isOpening(p)) {
            p.sendMessage(net.coremc.foundation.CoreFoundation.getInstance().messages().parse(
                    "<prefix> <red>Please wait for your current lootbox to finish opening.</red>",
                    plugin.getConfig().getString("prefix")));
            return;
        }
        // Permission check.
        if (cfg.hasPermission() && !p.hasPermission(cfg.permission())) {
            CoreFoundation.getInstance().messages().send(p, "error.no-permission");
            return;
        }

        // Server-side reward selection — done BEFORE animation starts.
        // The reward is securely chosen here but hidden from the player until reveal.
        final Reward winner = cfg.pickRandom();
        if (winner == null) {
            p.sendMessage(CoreFoundation.getInstance().messages().parse(
                    "<prefix> <red>This lootbox has no rewards configured.</red>",
                    plugin.getConfig().getString("prefix")));
            return;
        }

        // Build the opening session and register the anti-spam lock BEFORE the animation.
        final LootboxSession session = new LootboxSession(p.getUniqueId(), cfg, winner);
        activeOpenings.put(p.getUniqueId(), new ActiveOpening(session));

        // Decrement exactly one lootbox from the player's inventory (any slot / hand / offhand).
        decrementLootbox(p, cfg);

        // Start the world-space (non-GUI) animation with a tiny delay for the stagger effect.
        final long delay = 1L;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) {
                    activeOpenings.remove(p.getUniqueId());
                    return;
                }
                final LootboxWorldAnimation opening = new LootboxWorldAnimation(plugin, LootboxManager.this, session);
                opening.start();
            }
        }.runTaskLater(plugin, delay);
    }

    /**
     * Decrement exactly ONE lootbox of the given config from the player's inventory.
     * Searches main hand, off hand and every inventory slot so a stack held anywhere
     * is handled correctly (a x10 stack becomes x9, never removed entirely or reset to 1).
     */
    private void decrementLootbox(final Player p, final LootboxConfig cfg) {
        // Main hand.
        final ItemStack main = p.getInventory().getItemInMainHand();
        if (matches(main, cfg)) {
            if (main.getAmount() <= 1) p.getInventory().setItemInMainHand(null);
            else main.setAmount(main.getAmount() - 1);
            return;
        }
        // Off hand.
        final ItemStack off = p.getInventory().getItemInOffHand();
        if (matches(off, cfg)) {
            if (off.getAmount() <= 1) p.getInventory().setItemInOffHand(null);
            else off.setAmount(off.getAmount() - 1);
            return;
        }
        // Any other slot.
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            final ItemStack it = p.getInventory().getItem(i);
            if (matches(it, cfg)) {
                if (it.getAmount() <= 1) p.getInventory().setItem(i, null);
                else it.setAmount(it.getAmount() - 1);
                return;
            }
        }
    }

    /** True if the item is exactly the lootbox config (id match via PDC). */
    private boolean matches(final ItemStack item, final LootboxConfig cfg) {
        if (item == null) return false;
        final LootboxConfig found = get(item);
        return found != null && found.id().equals(cfg.id());
    }

    /** Grant the winning reward to the player (called after animation completes). */
    public void grantReward(final Player p, final LootboxSession session) {
        if (!p.isOnline()) return;
        activeOpenings.remove(p.getUniqueId());

        final Reward winner = session.winningReward();
        if (winner == null) return;

        // Grant the reward via the existing RewardResolver — no duplicate system.
        winner.apply(rewards, p);

        // Log the opening.
        plugin.getLogger().info("[lootbox] " + p.getName() + " opened " + session.box().id()
                + " -> " + winner.id() + " (" + winner.rarity() + ")");

        // Announce if configured.
        if (winner.announce() && winner.rarity().order() >= Rarity.RARE.order()) {
            final String colour = rarityMiniColour(winner.rarity());
            final String broadcast = "<prefix> <" + colour + ">" + p.getName()
                    + " <gray>opened a " + session.box().displayName()
                    + " and received <" + colour + ">" + winner.display() + "</" + colour + ">";
            Bukkit.broadcast(CoreFoundation.getInstance().messages().parse(broadcast,
                    plugin.getConfig().getString("prefix")));
        }
    }

    /** Cancel a pending opening — release the anti-spam lock without granting rewards. */
    public void cancelLegacy(final UUID playerId) {
        activeOpenings.remove(playerId);
    }

    /** Check if a player is currently in the opening animation (anti-spam). */
    public boolean isOpening(final Player p) {
        return activeOpenings.containsKey(p.getUniqueId());
    }

    private static Sound parseSound(final String name) {
        try {
            return Sound.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException | NullPointerException e) {
            return Sound.BLOCK_ENDER_CHEST_OPEN;
        }
    }

    private static String rarityMiniColour(final Rarity rarity) {
        return switch (rarity) {
            case COMMON -> "white";
            case UNCOMMON -> "yellow";
            case RARE -> "aqua";
            case EPIC -> "light_purple";
            case LEGENDARY -> "gold";
            case MYTHIC -> "dark_red";
        };
    }

    /** Session data for a GUI-based lootbox opening. */
    public record LootboxSession(UUID playerId, LootboxConfig box, Reward winningReward,
                                List<Reward> shuffledDisplayRewards) {
        public LootboxSession(final UUID playerId, final LootboxConfig box, final Reward winningReward) {
            this(playerId, box, winningReward, shuffleDisplayRewards(box, winningReward));
        }

        private static List<Reward> shuffleDisplayRewards(final LootboxConfig box, final Reward winning) {
            final List<Reward> pool = new ArrayList<>(box.rewards());
            // Remove the actual winner so it's not shown in the rolling display.
            pool.removeIf(r -> r.id().equals(winning.id()));
            Collections.shuffle(pool);
            return pool;
        }

        public Rarity winningRarity() {
            return winningReward != null ? winningReward.rarity() : Rarity.COMMON;
        }

        public void setAnimationTask(final org.bukkit.scheduler.BukkitTask task) {
            // The task is managed externally; this method exists for interface compatibility.
        }
    }

    /** Session data for a GUI-based lootbox opening. */
    private static final class ActiveOpening {
        final LootboxSession session;

        ActiveOpening(final LootboxSession session) {
            this.session = session;
        }
    }
}
