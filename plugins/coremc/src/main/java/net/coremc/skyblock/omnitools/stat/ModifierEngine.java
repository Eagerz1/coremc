package net.coremc.skyblock.omnitools.stat;

import java.util.EnumMap;
import java.util.Map;

/**
 * Central modifier aggregator for the OmniTool system.
 *
 * <p>A {@code Modifier} is a bonus to a {@link Stat}: either a percent bonus
 * (stacked additively on a base of 1.0) or a flat bonus. Listeners that want to
 * know "how much faster does this player mine" ask the engine instead of reading
 * config scattered across listeners.</p>
 *
 * <p>Building a snapshot is the ONLY place where omni-level, upgrade levels, perks,
 * abilities and rebirth perks are combined — so balancing lives in config, not in
 * code. See {@link net.coremc.skyblock.omnitools.tool.ToolManager#buildModifiers}
 * for how the per-player, per-role snapshot is assembled.</p>
 */
public final class ModifierEngine {

    private ModifierEngine() {}

    /** A single bonus to a stat. Percent bonuses stack on base 1.0; flat are added. */
    public record Modifier(Stat stat, double value, boolean percent) {
        public static Modifier percent(final Stat stat, final double value) {
            return new Modifier(stat, value, true);
        }
        public static Modifier flat(final Stat stat, final double value) {
            return new Modifier(stat, value, false);
        }
    }

    /** Accumulates modifiers for a player snapshot, then resolves final multipliers. */
    public static final class Snapshot {
        private final Map<Stat, Double> percent = new EnumMap<>(Stat.class);
        private final Map<Stat, Double> flat = new EnumMap<>(Stat.class);

        public Snapshot add(final Modifier m) {
            final Map<Stat, Double> map = m.percent() ? percent : flat;
            map.put(m.stat(), map.getOrDefault(m.stat(), 0.0) + m.value());
            return this;
        }

        /** Multiplier for a percent stat = 1.0 + sum(percent bonuses) + sum(flat bonuses). */
        public double multiplier(final Stat stat) {
            final double p = percent.getOrDefault(stat, 0.0);
            final double f = flat.getOrDefault(stat, 0.0);
            return 1.0 + p + f;
        }

        /** Raw percent bonus (for display), excluding the implicit 1.0 base. */
        public double percentBonus(final Stat stat) {
            return percent.getOrDefault(stat, 0.0) + flat.getOrDefault(stat, 0.0);
        }

        public boolean has(final Stat stat) {
            return percent.containsKey(stat) || flat.containsKey(stat);
        }
    }

    public static Snapshot snapshot() {
        return new Snapshot();
    }
}
