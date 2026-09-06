package net.coremc.corestaff.listener;
import org.bukkit.plugin.java.JavaPlugin;


import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Blocks movement for frozen players.
 */
public final class FreezeListener implements Listener {

    private final JavaPlugin plugin;
    private final Set<UUID> frozen;

    public FreezeListener(final JavaPlugin plugin, final Set<UUID> frozen) {
        this.plugin = plugin;
        this.frozen = frozen;
    }

    @EventHandler
    public void onMove(final PlayerMoveEvent event) {
        if (frozen.contains(event.getPlayer().getUniqueId())) {
            // Allow only head rotation, not position change.
            if (event.getFrom().getX() != event.getTo().getX()
                    || event.getFrom().getZ() != event.getTo().getZ()
                    || event.getFrom().getY() != event.getTo().getY()) {
                event.setTo(event.getFrom());
            }
        }
    }
}
