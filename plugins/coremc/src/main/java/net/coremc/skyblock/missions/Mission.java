package net.coremc.skyblock.missions;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * A single configurable mission. Categories map to existing SkyBlock activities:
 * mining, farming, fishing, logging, slaying, generators, spawners, island.
 *
 * <p>Progress is tracked per-player and incremented by the activity listeners.
 * When {@code progress >= requirement} the mission can be claimed once
 * (unless {@code repeatable}), dispensing the configured rewards through the
 * existing CoreMC economies (money, Sky Tokens, Credits, keys, items, island XP).</p>
 */
public final class Mission {

    public enum Category {
        MINING, FARMING, FISHING, LOGGING, SLAYING, GENERATORS, SPAWNERS, ISLAND
    }

    private final String id;
    private final Category category;
    private final String name;
    private final String description;
    private final long requirement;
    private final boolean repeatable;
    private final Reward reward;

    public Mission(final String id, final ConfigurationSection s) {
        this.id = id;
        this.category = parseCategory(s.getString("category", s.getString("type", "mining")));
        this.name = s.getString("name", id);
        this.description = s.getString("description", "");
        this.requirement = Math.max(1, s.getLong("requirement", 1));
        this.repeatable = s.getBoolean("repeatable", false);
        this.reward = new Reward(s.getConfigurationSection("reward"));
    }

    private static Category parseCategory(final String raw) {
        if (raw == null) return Category.MINING;
        try {
            return Category.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return Category.MINING;
        }
    }

    public String id() { return id; }
    public Category category() { return category; }
    public String name() { return name; }
    public String description() { return description; }
    public long requirement() { return requirement; }
    public boolean repeatable() { return repeatable; }
    public Reward reward() { return reward; }

    /** The activity key used to increment per-player progress. */
    public String progressKey() {
        return category.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Reward payload, integrated with the existing CoreMC currencies. */
    public static final class Reward {
        public final double money;
        public final long tokens;
        public final long credits;
        public final long islandXp;
        public final int skyKeys;
        public final int riverKeys;
        public final int crimsonKeys;
        public final int voteKeys;
        public final int monthlyKeys;
        public final int eventKeys;
        public final int boostKeys;
        public final List<String> items; // "MATERIAL:amount" entries

        public Reward(final ConfigurationSection s) {
            if (s == null) {
                this.money = 0; this.tokens = 0; this.credits = 0; this.islandXp = 0;
                this.skyKeys = 0; this.riverKeys = 0; this.crimsonKeys = 0; this.voteKeys = 0;
                this.monthlyKeys = 0; this.eventKeys = 0; this.boostKeys = 0;
                this.items = new ArrayList<>();
                return;
            }
            this.money = s.getDouble("money", 0);
            this.tokens = s.getLong("tokens", 0);
            this.credits = s.getLong("credits", 0);
            this.islandXp = s.getLong("island-xp", 0);
            this.skyKeys = s.getInt("keys.sky", 0);
            this.riverKeys = s.getInt("keys.river", 0);
            this.crimsonKeys = s.getInt("keys.crimson", 0);
            this.voteKeys = s.getInt("keys.vote", 0);
            this.monthlyKeys = s.getInt("keys.monthly", 0);
            this.eventKeys = s.getInt("keys.event", 0);
            this.boostKeys = s.getInt("keys.boost", 0);
            this.items = s.getStringList("items");
        }

        public boolean isEmpty() {
            return money <= 0 && tokens <= 0 && credits <= 0 && islandXp <= 0
                    && skyKeys <= 0 && riverKeys <= 0 && crimsonKeys <= 0 && voteKeys <= 0
                    && monthlyKeys <= 0 && eventKeys <= 0 && boostKeys <= 0 && items.isEmpty();
        }
    }
}
