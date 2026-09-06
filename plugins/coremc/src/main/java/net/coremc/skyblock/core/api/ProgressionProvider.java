package net.coremc.skyblock.core.api;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bridge to skyblock-progression for upgrades and buffs. skyblock-progression
 * implements this and registers itself on enable so skyblock-core can delegate.
 * If no provider is registered, calls are no-ops / return defaults.
 */
public interface ProgressionProvider {

    /** Whether the player has an island upgrade (e.g. "size", "mob-limit"). */
    boolean hasUpgrade(int islandId, @NotNull String upgradeId);

    /** Get the current level (tier) of an island upgrade; 0 = not purchased. */
    int getUpgradeLevel(int islandId, @NotNull String upgradeId);

    /** Get an island-wide buff multiplier by buff id (1.0 = baseline). */
    double getBuffMultiplier(int islandId, @NotNull String buffId);
}
