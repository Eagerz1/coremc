package net.coremc.skyblock.core.listener;
import org.bukkit.plugin.java.JavaPlugin;

import net.coremc.foundation.CoreFoundation;

import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.storage.Island;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Island protection + invite auto-join.
 */
public final class IslandListener implements Listener {

    private final JavaPlugin plugin;
    private final IslandApi api;

    public IslandListener(final JavaPlugin plugin, final IslandApi api) {
        this.plugin = plugin;
        this.api = api;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final int islandId = api.getInvite(player.getUniqueId());
        if (islandId >= 0) {
            final net.coremc.skyblock.core.storage.Island is = api.getIsland(islandId);
            if (is != null) {
                api.joinIsland(islandId, player.getUniqueId());
                api.clearInvite(player.getUniqueId());
                CoreFoundation.getInstance().messages().sendRaw(player,
                        CoreFoundation.getInstance().messages().getPrefix()
                                + " <green>You joined an island! Use /is go.");
            }
        }
    }

    @EventHandler
    public void onBreak(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        // OP (or bypass permission) is never constrained by island protection.
        if (player.isOp() || player.hasPermission("coremc.admin.bypass")) {
            return;
        }
        if (!api.canBuild(player.getUniqueId(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(final BlockPlaceEvent event) {
        final Player player = event.getPlayer();
        // OP (or bypass permission) is never constrained by island protection.
        if (player.isOp() || player.hasPermission("coremc.admin.bypass")) {
            return;
        }
        if (!api.canBuild(player.getUniqueId(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof final Player victim)) {
            return;
        }
        if (!(event.getDamager() instanceof final Player attacker)) {
            return;
        }
        // Only enforce no-PvP inside an island region, and only against members.
        final Island victimIsland = api.getIsland(victim.getUniqueId());
        if (victimIsland == null) {
            return;
        }
        final Location loc = victim.getLocation();
        final Island region = api.islandAt(loc);
        // The victim must be inside an island region, and the attacker must be
        // allowed to build there (owner/member). If the attacker cannot build
        // (i.e. a visitor), block the hit.
        if (region != null && !api.canBuild(attacker.getUniqueId(), loc)) {
            event.setCancelled(true);
        }
    }
}
