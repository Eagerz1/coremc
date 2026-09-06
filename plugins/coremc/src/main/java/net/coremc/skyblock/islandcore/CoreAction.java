package net.coremc.skyblock.islandcore;

import java.util.Locale;

/**
 * The category of activity that produced a Core contribution.
 *
 * <p>Extensible: add a new enum value (and a contribution source) to support a
 * future action type without touching the Core service. Unknown config strings
 * fall back to {@link #OTHER}.</p>
 */
public enum CoreAction {

    /** A CoreMC spawner mob was killed. */
    MOB_KILL,
    /** Mining an ore / stone block (role activity). */
    MINING,
    /** Harvesting a crop (role activity). */
    FARMING,
    /** Chopping a log (role activity). */
    LOGGING,
    /** Catching a fish (role activity). */
    FISHING,
    /** Catch-all for future contribution sources. */
    OTHER;

    public static CoreAction from(final String raw) {
        if (raw == null) return OTHER;
        try {
            return CoreAction.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return OTHER;
        }
    }
}
