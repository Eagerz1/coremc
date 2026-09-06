package net.coremc.store.api;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Event;

/** Fired when a player's Credits balance changes. */
public final class CreditChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final OfflinePlayer player;
    private final double delta;
    private final double newBalance;
    public CreditChangeEvent(final OfflinePlayer player, final double delta, final double newBalance) {
        this.player = player; this.delta = delta; this.newBalance = newBalance;
    }
    public OfflinePlayer player() { return player; }
    public double delta() { return delta; }
    public double newBalance() { return newBalance; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
