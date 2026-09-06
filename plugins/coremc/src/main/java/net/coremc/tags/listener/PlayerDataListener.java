package net.coremc.tags.listener;

import net.coremc.tags.manager.TagManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Preloads ownership into the cache on join and releases it on quit. */
public final class PlayerDataListener implements Listener {

    private final TagManager manager;

    public PlayerDataListener(final net.coremc.tags.CoreTags plugin, final TagManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent e) {
        manager.ensureLoaded(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent e) {
        manager.invalidate(e.getPlayer().getUniqueId());
    }
}
