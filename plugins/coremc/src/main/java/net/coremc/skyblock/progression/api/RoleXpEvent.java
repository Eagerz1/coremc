package net.coremc.skyblock.progression.api;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired when role XP is awarded to a player for a tree (before it is credited). */
public final class RoleXpEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID player;
    private String tree;
    private long amount;
    private boolean cancelled;

    public RoleXpEvent(final UUID player, final String tree, final long amount) {
        this.player = player;
        this.tree = tree;
        this.amount = amount;
    }

    public UUID getPlayer() { return player; }
    public String getTree() { return tree; }
    public void setTree(final String tree) { this.tree = tree; }
    public long getAmount() { return amount; }
    public void setAmount(final long amount) { this.amount = Math.max(0, amount); }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(final boolean cancel) { cancelled = cancel; }

    public static HandlerList getHandlerList() { return HANDLERS; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
}
