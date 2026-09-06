package net.coremc.skyblock.events;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Shows the live event boss bar to players who join while an event is active,
 * and forgets them on quit. The {@link EventManager} owns the bar instance.
 */
public final class EventsJoinListener implements Listener {

    private final EventManager mgr;

    public EventsJoinListener(final EventManager mgr) {
        this.mgr = mgr;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent ev) {
        final Player p = ev.getPlayer();
        Bukkit.getScheduler().runTaskLater(mgr.plugin(), () -> mgr.onJoin(p), 1L);
    }

    @EventHandler
    public void onQuit(final org.bukkit.event.player.PlayerQuitEvent ev) {
        mgr.onQuit(ev.getPlayer());
    }
}
