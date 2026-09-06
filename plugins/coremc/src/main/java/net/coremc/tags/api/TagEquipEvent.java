package net.coremc.tags.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired when a player equips a (different) tag. */
public final class TagEquipEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String tagId;
    private final String previous;
    public TagEquipEvent(final Player player, final String tagId, final String previous) {
        super(player); this.tagId = tagId; this.previous = previous;
    }
    public String tagId() { return tagId; }
    public String previous() { return previous; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
