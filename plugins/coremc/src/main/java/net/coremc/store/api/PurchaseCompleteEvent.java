package net.coremc.store.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired when a purchase is completed (rewards delivered). */
public final class PurchaseCompleteEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Purchase purchase;
    public PurchaseCompleteEvent(final Player player, final Purchase purchase) {
        super(player); this.purchase = purchase;
    }
    public Purchase purchase() { return purchase; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
