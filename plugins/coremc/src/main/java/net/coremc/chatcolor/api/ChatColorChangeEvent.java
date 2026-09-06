package net.coremc.chatcolor.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired when a player changes their chat colour. */
public final class ChatColorChangeEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String colourId;

    public ChatColorChangeEvent(final Player player, final String colourId) {
        super(player);
        this.colourId = colourId;
    }

    public String colourId() {
        return colourId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
