package net.coremc.store.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired when a rank purchase is delivered. */
public final class RankPurchaseEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String rankId;
    public RankPurchaseEvent(final Player player, final String rankId) {
        super(player); this.rankId = rankId;
    }
    public String rankId() { return rankId; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
