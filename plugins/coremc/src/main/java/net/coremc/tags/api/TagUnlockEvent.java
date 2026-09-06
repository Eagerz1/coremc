package net.coremc.tags.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired when a player unlocks a tag. */
public final class TagUnlockEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String tagId;
    public TagUnlockEvent(final Player player, final String tagId) {
        super(player); this.tagId = tagId;
    }
    public String tagId() { return tagId; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
