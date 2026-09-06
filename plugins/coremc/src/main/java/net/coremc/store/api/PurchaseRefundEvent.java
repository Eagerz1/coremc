package net.coremc.store.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired when a purchase is refunded. */
public final class PurchaseRefundEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Purchase purchase;
    public PurchaseRefundEvent(final Player player, final Purchase purchase) {
        super(player); this.purchase = purchase;
    }
    public Purchase purchase() { return purchase; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
