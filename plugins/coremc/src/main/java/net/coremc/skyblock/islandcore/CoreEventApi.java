package net.coremc.skyblock.islandcore;

import org.jetbrains.annotations.NotNull;

/**
 * Temporary live-event hooks for the Core system.
 *
 * <p>Parallel to {@link CoreBuffApi}: the Core service multiplies configured
 * base values by these multipliers so seasonal / temporary events can boost
 * contribution, progression, token generation and sell value. The Core service
 * does NOT build its own event calendar — it only consumes whatever a registered
 * provider returns. Default (no provider / no active event) is identity.</p>
 *
 * <p>Register a provider via {@link CoreService#setEventApi(CoreEventApi)}.
 * A default implementation ({@link CoreEventsFromManager}) bridges the existing
 * {@code EventManager} so the new CORE_CONTRIBUTION / CORE_PROGRESSION / MOB_DROP
 * event types work without duplication.</p>
 */
public interface CoreEventApi {

    /** Multiplier for Core money / progression contribution (1.0 = none). */
    default double contributionMultiplier() { return 1.0; }

    /** Multiplier for mob-unlock progression (kills credited) (1.0 = none). */
    default double progressionMultiplier() { return 1.0; }

    /** Multiplier for Sky Token generation (1.0 = none). */
    default double skyTokenMultiplier() { return 1.0; }

    /** Multiplier for drop sell value (1.0 = none). */
    default double sellMultiplier() { return 1.0; }

    /** Human-readable active event name for debug / future UI (empty string if none). */
    default @NotNull String activeEventName() { return ""; }
}
