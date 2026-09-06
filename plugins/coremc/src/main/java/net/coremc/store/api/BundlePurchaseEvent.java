package net.coremc.store.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired when a bundle purchase is delivered. */
public final class BundlePurchaseEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String bundleId;
    public BundlePurchaseEvent(final Player player, final String bundleId) {
        super(player); this.bundleId = bundleId;
    }
    public String bundleId() { return bundleId; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
