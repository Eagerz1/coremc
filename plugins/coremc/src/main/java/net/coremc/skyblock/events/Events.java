package net.coremc.skyblock.events;

import net.coremc.coremc.CoreMC;
import org.jetbrains.annotations.Nullable;

/**
 * Static, null-safe access to the live {@link EventManager} multipliers.
 *
 * <p>Existing reward systems (Island XP, role currency, Omnitool progress) call
 * these helpers at their single central award point, so the doubling logic lives
 * in exactly one place and is never duplicated. If the event system is disabled or
 * not yet loaded, every query returns {@code 1.0} (no effect).</p>
 *
 * <p>For role-scoped events (e.g. SLAYING_COINS) pass the role so the multiplier only
 * applies to that role's currency and not every role.</p>
 */
public final class Events {

    private Events() {}

    /** Multiplier for shared island XP (1.0 when no event / wrong type). */
    public static double islandXpMultiplier() {
        final EventManager m = manager();
        return m == null ? 1.0 : m.islandXpMultiplier();
    }

    /** Multiplier for a role's currency (Slaying Coins = role "SLAYING"). 1.0 unless this
     *  is a role-scoped event matching {@code role}. */
    public static double roleCurrencyMultiplier(@Nullable final String role) {
        final EventManager m = manager();
        if (m == null) return 1.0;
        // Slaying Coins event doubles the SLAYING role currency specifically.
        if (m.slayingCoinsMultiplier() != 1.0 && "SLAYING".equalsIgnoreCase(role)) {
            return m.slayingCoinsMultiplier();
        }
        return 1.0;
    }

    /** Multiplier for per-Omnitool tool XP (any role). 1.0 when no event / wrong type. */
    public static double omniToolMultiplier() {
        final EventManager m = manager();
        return m == null ? 1.0 : m.omniToolMultiplier();
    }

    /** Multiplier for Core money + progression contribution (1.0 when no event / wrong type). */
    public static double coreContributionMultiplier() {
        final EventManager m = manager();
        return m == null ? 1.0 : m.multiplierFor(GameEventType.CORE_CONTRIBUTION);
    }

    /** Multiplier for Core mob-unlock progression (1.0 when no event / wrong type). */
    public static double coreProgressionMultiplier() {
        final EventManager m = manager();
        return m == null ? 1.0 : m.multiplierFor(GameEventType.CORE_PROGRESSION);
    }

    /** Multiplier for Core Sky Token generation (1.0 when no event / wrong type). */
    public static double coreTokenMultiplier() {
        final EventManager m = manager();
        return m == null ? 1.0 : m.multiplierFor(GameEventType.CORE_TOKENS);
    }

    /** Multiplier for Core drop / activity sell value (1.0 when no event / wrong type). */
    public static double coreSellMultiplier() {
        final EventManager m = manager();
        return m == null ? 1.0 : m.multiplierFor(GameEventType.CORE_SELL);
    }

    private static @Nullable EventManager manager() {
        try {
            final CoreMC mc = CoreMC.getInstance();
            return mc == null ? null : mc.events();
        } catch (final IllegalStateException e) {
            return null;
        }
    }
}
