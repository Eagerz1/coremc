package net.coremc.skyblock.progression.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired when Sky Tokens are awarded to a player (before they are credited). */
public final class TokenEarnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID player;
    private long amount;
    private boolean cancelled;

    public TokenEarnEvent(final UUID player, final long amount) {
        this.player = player;
        this.amount = amount;
    }

    public UUID getPlayer() { return player; }
    public long getAmount() { return amount; }
    public void setAmount(final long amount) { this.amount = Math.max(0, amount); }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(final boolean cancel) { cancelled = cancel; }

    public static HandlerList getHandlerList() { return HANDLERS; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
}
