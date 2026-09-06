package net.coremc.skyblock.omnitools.stat;

/**
 * Central registry of every stat an Omnitool can modify.
 *
 * <p>The {@link ModifierEngine} aggregates bonuses (percent or flat) keyed by these
 * stats so individual listeners never hardcode numbers. New stats are added by
 * extending this enum only — every consumer (effects, GUI, lore) reads from it.</p>
 *
 * <p>Each modifier is combined centrally: percent bonuses stack additively on top of
 * the base (1.0), flat bonuses are added last. The resulting multiplier applies to
 * the relevant game value.</p>
 */
public enum Stat {

    // ---- Efficiency / speed ----
    /** Block break speed (haste) for the tool's activity. */
    MINING_SPEED("Mining Speed", StatType.PERCENT),
    /** Crop / harvest action speed. */
    FARMING_SPEED("Farming Speed", StatType.PERCENT),
    /** Log felling speed. */
    LOGGING_SPEED("Logging Speed", StatType.PERCENT),
    /** Fishing reel / bite speed. */
    FISHING_SPEED("Fishing Speed", StatType.PERCENT),
    /** Generic action speed for the UNIVERSAL role (covers every activity a bit). */
    UNIVERSAL_SPEED("Universal Speed", StatType.PERCENT),

    // ---- Yields / drops ----
    /** Chance to get an extra ore/drop when mining (percent). */
    MINING_DROPS("Mining Drops", StatType.PERCENT),
    /** Chance for bonus crops when harvesting (percent). */
    FARMING_YIELD("Farming Yield", StatType.PERCENT),
    /** Chance for extra logs when felling trees (percent). */
    LOGGING_YIELD("Logging Yield", StatType.PERCENT),
    /** Chance for bonus fish / better fishing loot (percent). */
    FISHING_REWARDS("Fishing Rewards", StatType.PERCENT),
    /** Generic yield across activities for the UNIVERSAL role. */
    UNIVERSAL_YIELD("Universal Yield", StatType.PERCENT),

    // ---- Combat ----
    /** Extra damage dealt with the Slaying Omnitool (percent). */
    MOB_DAMAGE("Mob Damage", StatType.PERCENT),
    /** Chance for bonus mob loot (percent). */
    MOB_DROPS("Mob Drops", StatType.PERCENT),

    // ---- Progression multipliers ----
    /** Multiplies role XP gained from the activity (percent, additive to base 1.0). */
    XP_MULTIPLIER("XP Multiplier", StatType.PERCENT),
    /** Multiplies role currency gained from the activity (percent). */
    CURRENCY_MULTIPLIER("Currency Multiplier", StatType.PERCENT),
    /** Multiplies Core contribution (CoreMC progression currency) gained (percent). */
    CORE_CONTRIBUTION_MULTIPLIER("Core Contribution", StatType.PERCENT),
    /** Multiplies Sky Tokens gained from the activity (percent). */
    TOKEN_MULTIPLIER("Token Multiplier", StatType.PERCENT),
    /** Multiplies sell / economy value gained (percent). */
    ECONOMY_MULTIPLIER("Economy Value", StatType.PERCENT);

    /** How a modifier of this stat combines. */
    public enum StatType {
        /** Stacks additively on top of 1.0 (e.g. +5% per source). */
        PERCENT,
        /** Added directly to a base value (used rarely; most stats are percent). */
        FLAT
    }

    private final String display;
    private final StatType type;

    Stat(final String display, final StatType type) {
        this.display = display;
        this.type = type;
    }

    public String display() { return display; }
    public StatType type() { return type; }

    /** Parse a config string (e.g. "mining_speed") to a Stat, or null. */
    public static Stat fromConfig(final String raw) {
        if (raw == null) return null;
        try {
            return Stat.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
