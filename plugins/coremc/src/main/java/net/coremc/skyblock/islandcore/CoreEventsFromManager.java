package net.coremc.skyblock.islandcore;

import net.coremc.coremc.CoreMC;
import net.coremc.skyblock.events.EventManager;
import net.coremc.skyblock.events.Events;
import net.coremc.skyblock.events.GameEventType;
import org.jetbrains.annotations.NotNull;

/**
 * Default {@link CoreEventApi} bridging the existing {@link EventManager} into
 * the Core system. The new {@code CORE_CONTRIBUTION}, {@code CORE_PROGRESSION},
 * {@code CORE_TOKENS} and {@code CORE_SELL} event types map onto this manager's
 * typed multiplier queries, so a seasonal event that selects one of those types
 * automatically boosts Core activity — no Core-side duplication of scheduling.
 *
 * <p>When the event system is unavailable or no event is active, every query
 * returns 1.0 (identity).</p>
 */
public final class CoreEventsFromManager implements CoreEventApi {

    @Override
    public double contributionMultiplier() {
        return mult(GameEventType.CORE_CONTRIBUTION);
    }

    @Override
    public double progressionMultiplier() {
        return mult(GameEventType.CORE_PROGRESSION);
    }

    @Override
    public double skyTokenMultiplier() {
        return mult(GameEventType.CORE_TOKENS);
    }

    @Override
    public double sellMultiplier() {
        return mult(GameEventType.CORE_SELL);
    }

    @Override
    public @NotNull String activeEventName() {
        final EventManager m = manager();
        return m == null || !m.isActive() ? "" : m.activeDefinition().displayName();
    }

    private static double mult(final GameEventType type) {
        final EventManager m = manager();
        if (m == null) return 1.0;
        return m.multiplierFor(type);
    }

    private static EventManager manager() {
        try {
            return CoreMC.getInstance().events();
        } catch (final IllegalStateException e) {
            return null;
        }
    }
}
