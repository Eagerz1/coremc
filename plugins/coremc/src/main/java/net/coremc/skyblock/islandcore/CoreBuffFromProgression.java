package net.coremc.skyblock.islandcore;

import net.coremc.coremc.CoreMC;

/**
 * Default {@link CoreBuffApi} backed by the existing island progression buff
 * architecture ({@code ProgressionProvider#getBuffMultiplier}).
 *
 * <p>Reads multipliers for the Core-specific effect ids
 * {@code core_money} / {@code core_sell} / {@code core_tokens}. When a future
 * island upgrade tree node uses one of those effects, its level automatically
 * feeds the Core system — no Core-side change required. Until then every method
 * returns 1.0 (identity), so the foundation is fully functional standalone.</p>
 */
public final class CoreBuffFromProgression implements CoreBuffApi {

    @Override
    public double coreMoneyMultiplier(final int islandId) {
        return CoreMC.getInstance().progression().getBuffMultiplier(islandId, "core_money");
    }

    @Override
    public double sellMultiplier(final int islandId) {
        return CoreMC.getInstance().progression().getBuffMultiplier(islandId, "core_sell");
    }

    @Override
    public double skyTokenMultiplier(final int islandId) {
        return CoreMC.getInstance().progression().getBuffMultiplier(islandId, "core_tokens");
    }
}
