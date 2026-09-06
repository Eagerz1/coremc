package net.coremc.skyblock.islandcore;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Cancellable pre-contribution hook fired by {@link CoreService#contribute}
 * before any Core money / tokens / progression are applied.
 *
 * <p>Future systems (seasonal events, quest gates, anti-abuse) can cancel or
 * inspect the {@link CoreContributionContext} here without the Core service
 * knowing about them.</p>
 */
public final class CoreContributeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CoreContributionContext context;
    private boolean cancelled = false;

    public CoreContributeEvent(final CoreContributionContext context) {
        this.context = context;
    }

    public CoreContributionContext context() { return context; }

    public boolean isCancelled() { return cancelled; }

    public void setCancelled(final boolean b) { this.cancelled = b; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
