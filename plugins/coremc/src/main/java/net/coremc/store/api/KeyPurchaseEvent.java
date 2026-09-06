package net.coremc.store.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired when a key purchase is delivered. */
public final class KeyPurchaseEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String keyId;
    public KeyPurchaseEvent(final Player player, final String keyId) {
        super(player); this.keyId = keyId;
    }
    public String keyId() { return keyId; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
