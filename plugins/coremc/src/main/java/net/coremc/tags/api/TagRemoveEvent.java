package net.coremc.tags.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired when a tag is removed from a player. */
public final class TagRemoveEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String tagId;
    public TagRemoveEvent(final Player player, final String tagId) {
        super(player); this.tagId = tagId;
    }
    public String tagId() { return tagId; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
