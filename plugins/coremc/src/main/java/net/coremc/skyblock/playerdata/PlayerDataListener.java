package net.coremc.skyblock.playerdata;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Loads a player's permanent data on join and flushes + unloads it on quit.
 * Loading on join means an update that replaced every config file never touches
 * the player's data — it is read fresh from {@code data/players/<uuid>.yml}.
 */
public final class PlayerDataListener implements Listener {

    private final PlayerDataManager manager;

    public PlayerDataListener(final PlayerDataManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent e) {
        manager.loadForJoin(e.getPlayer().getUniqueId(), e.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent e) {
        manager.unload(e.getPlayer().getUniqueId());
    }
}
