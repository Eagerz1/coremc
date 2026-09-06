package net.coremc.skyblock.progression.role;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the selected activity role per player, plus per-(player,role) XP and
 * derived role level. Role XP belongs to the player; island progression is
 * handled separately by the tree system. Perks come from owned tree nodes.
 */
public final class RoleManager {

    /** Activity roles a player can select (Island is NOT a player role). */
    public enum Role {
        MINING, FARMING, FISHING, LOGGING, SLAYING, UNIVERSAL;

        public String display() {
            return name().charAt(0) + name().substring(1).toLowerCase();
        }

        /** Tree id this role progresses through. */
        public String tree() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** Whether this role is the flexible "a bit of everything" role. */
        public boolean universal() {
            return this == UNIVERSAL;
        }
    }

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Role> selected = new ConcurrentHashMap<>();
    private final Map<String, Long> xp = new ConcurrentHashMap<>(); // key = uuid + ":" + tree
    // Claimed one-time milestone levels per player+role (key = uuid + ":" + role).
    private final Map<String, Set<Integer>> claimedMilestones = new ConcurrentHashMap<>();

    public RoleManager(final org.bukkit.plugin.java.JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "roles.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection sel = data.getConfigurationSection("selected");
        if (sel != null) {
            for (final String k : sel.getKeys(false)) {
                try {
                    selected.put(UUID.fromString(k), Role.valueOf(sel.getString(k)));
                } catch (final IllegalArgumentException ignored) {}
            }
        }
        final ConfigurationSection xpSec = data.getConfigurationSection("xp");
        if (xpSec != null) {
            for (final String k : xpSec.getKeys(false)) {
                xp.put(k, xpSec.getLong(k));
            }
        }
        final ConfigurationSection msSec = data.getConfigurationSection("claimed-milestones");
        if (msSec != null) {
            for (final String k : msSec.getKeys(false)) {
                final Set<Integer> set = new java.util.LinkedHashSet<>();
                for (final int v : msSec.getIntegerList(k)) set.add(v);
                claimedMilestones.put(k, set);
            }
        }
    }

    public Role get(final UUID uuid) {
        return selected.get(uuid);
    }

    public boolean set(final UUID uuid, final Role role) {
        selected.put(uuid, role);
        data.set("selected." + uuid.toString(), role.name());
        save();
        return true;
    }

    private static String xpKey(final UUID uuid, final String tree) {
        return uuid.toString() + ":" + tree;
    }

    private static String milestoneKey(final UUID uuid, final Role role) {
        return uuid.toString() + ":" + role.name();
    }

    /** Has this player already claimed the one-time milestone reward for {@code role} at {@code level}? */
    public boolean hasClaimedMilestone(final UUID uuid, final Role role, final int level) {
        final Set<Integer> set = claimedMilestones.get(milestoneKey(uuid, role));
        return set != null && set.contains(level);
    }

    /** Mark a milestone level as claimed (idempotent). */
    public void claimMilestone(final UUID uuid, final Role role, final int level) {
        claimedMilestones.computeIfAbsent(milestoneKey(uuid, role), k -> new java.util.LinkedHashSet<>()).add(level);
    }

    public long getXp(final UUID uuid, final String tree) {
        return xp.getOrDefault(xpKey(uuid, tree), 0L);
    }

    public void addXp(final UUID uuid, final String tree, final long amount) {
        if (amount <= 0) {
            return;
        }
        xp.put(xpKey(uuid, tree), getXp(uuid, tree) + amount);
    }

    /** Configurable, progressively-increasing XP thresholds (cumulative) — index = level. */
    public java.util.List<Long> levelXp() {
        final java.util.List<Long> list = plugin.getConfig().getLongList("role-level-xp");
        if (list.isEmpty()) {
            // Safe default: gentle ramp. Operators should configure this.
            return java.util.List.of(0L, 100L, 300L, 700L, 1500L, 3000L, 6000L, 12000L, 24000L, 50000L);
        }
        return list;
    }

    /** Configurable cap — roles cannot exceed this level. */
    public int maxLevel() {
        return Math.max(0, levelXp().size() - 1);
    }

    /** Role level derived from cumulative XP using configurable thresholds. */
    public int getLevel(final UUID uuid, final String tree) {
        final long total = getXp(uuid, tree);
        final java.util.List<Long> thresholds = levelXp();
        int lvl = 0;
        for (int i = 0; i < thresholds.size(); i++) {
            if (total >= thresholds.get(i)) {
                lvl = i;
            }
        }
        return Math.min(lvl, maxLevel());
    }

    /** XP needed to reach the next level, or -1 if maxed. */
    public long xpToNext(final UUID uuid, final String tree) {
        final long total = getXp(uuid, tree);
        final java.util.List<Long> thresholds = levelXp();
        for (final long t : thresholds) {
            if (total < t) {
                return t - total;
            }
        }
        return -1;
    }

    /** Cumulative XP threshold required to reach the next level (the target), or -1 if maxed. */
    public long nextLevelThreshold(final UUID uuid, final String tree) {
        final long total = getXp(uuid, tree);
        final java.util.List<Long> thresholds = levelXp();
        for (final long t : thresholds) {
            if (total < t) return t;
        }
        return -1;
    }

    /** Progress fraction (0..1) toward the next level, or 1.0 if maxed. */
    public double progress(final UUID uuid, final String tree) {
        final long total = getXp(uuid, tree);
        final long need = nextLevelThreshold(uuid, tree);
        if (need < 0) return 1.0;
        final long base = need == 0 ? 0 : need - xpToNext(uuid, tree);
        final double pct = need == 0 ? 1.0 : (double) (total - base) / (need - base);
        return Math.max(0.0, Math.min(1.0, pct));
    }

    /** Build a progress-bar + numbers string for display (█/░, configurable width). */
    public String progressBar(final UUID uuid, final String tree, final int width) {
        final int total = width <= 0 ? 20 : width;
        final int filled = (int) Math.round(progress(uuid, tree) * total);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < total; i++) sb.append(i < filled ? "█" : "░");
        return sb.toString();
    }

    public void save() {
        for (final var e : selected.entrySet()) {
            data.set("selected." + e.getKey().toString(), e.getValue().name());
        }
        for (final var e : xp.entrySet()) {
            data.set("xp." + e.getKey(), e.getValue());
        }
        for (final var e : claimedMilestones.entrySet()) {
            data.set("claimed-milestones." + e.getKey(), new java.util.ArrayList<>(e.getValue()));
        }
        try {
            data.save(file);
        } catch (final java.io.IOException e) {
            plugin.getLogger().warning("Could not save roles.yml: " + e.getMessage());
        }
    }
}
