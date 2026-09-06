package net.coremc.skyblock.spawners.listener;
import org.bukkit.plugin.java.JavaPlugin;
import net.coremc.coremc.CoreMC;

import net.coremc.foundation.CoreFoundation;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.spawners.spawner.PlacedSpawner;
import net.coremc.skyblock.spawners.spawner.SpawnerManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Picks a placed spawner back up when a player LEFT-clicks it on their island.
 *
 * <p>Right-click is reserved for placing (handled by {@link SpawnerListener}) and
 * must never remove a spawner. Left-click is the only interaction that picks one
 * up, and it correctly decrements the island's active spawner count (because the
 * count is derived from the live placed set).</p>
 */
public final class SpawnerPickupListener implements Listener {

    private final JavaPlugin plugin;
    private final SpawnerManager spawners;

    public SpawnerPickupListener(final JavaPlugin plugin, final SpawnerManager spawners) {
        this.plugin = plugin;
        this.spawners = spawners;
    }

    @EventHandler
    public void onLeftClick(final PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        final Player p = event.getPlayer();
        final Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        final PlacedSpawner at = spawners.getAt(clicked.getLocation());
        if (at == null) {
            return;
        }
        // Only shift + left-click picks the pile back up (matches the stacking UX, and
        // stops an accidental left-click from reclaiming a whole stack).
        if (!p.isSneaking()) {
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix()
                            + " <gray>Shift + left-click to pick up " + at.count() + " spawners.");
            return;
        }
        final IslandApi api = CoreMC.getInstance().islands().api();
        if (!api.canBuild(p.getUniqueId(), clicked.getLocation())) {
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix()
                            + " <red>You can only pick up spawners on your own island.");
            return;
        }
        if (spawners.pickup(clicked.getLocation(), p)) {
            event.setCancelled(true);
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix() + " <green>Picked up a spawner pile.");
        }
    }
}
