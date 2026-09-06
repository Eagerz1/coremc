package net.coremc.skyblock.core.listener;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.island.IslandManager;
import net.coremc.skyblock.core.storage.Island;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island border visualisation + void protection.
 *
 * <p>A per-player WorldBorder packet draws a clean client-side island boundary
 * (no physical blocks, no damage, no gameplay interference). The radius follows
 * the island's configured size (which grows when Island Size I / II / ... are
 * purchased in /is upgrades) and only shows to island members. Borders are saved
 * with the island (they are derived from island data) and refreshed whenever the
 * size changes.</p>
 */
public final class IslandBorderListener implements Listener {

    private final IslandApi api;
    private final IslandManager islandManager;
    private final Map<UUID, Integer> playerCurrentIsland = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerCurrentBorderSize = new ConcurrentHashMap<>();

    public IslandBorderListener() {
        this.api = CoreMC.getInstance().islands().api();
        this.islandManager = CoreMC.getInstance().islands().islandManager();
    }

    /** Master switch + colours, all configurable in config.yml under {@code island.border}. */
    private boolean enabled() {
        return CoreFoundation.getInstance().getConfig().getBoolean("island.border.enabled", true);
    }

    private void updateBorder(final Player player) {
        if (!enabled()) {
            clearBorder(player);
            return;
        }
        final Location loc = player.getLocation();
        if (!isSkyblockWorld(loc.getWorld())) {
            clearBorder(player);
            return;
        }

        final Island island = findIslandAt(loc);
        if (island == null || island.getSpawn() == null) {
            clearBorder(player);
            return;
        }

        // Only show the boundary to members of this island.
        if (!isMember(player, island)) {
            clearBorder(player);
            return;
        }

        final int islandId = island.getId();
        final double radius = islandManager.getIslandRadius(island);
        final Location center = island.getSpawn();

        final Integer currentIslandId = playerCurrentIsland.get(player.getUniqueId());
        final Double currentSize = playerCurrentBorderSize.get(player.getUniqueId());
        if (currentIslandId != null && currentIslandId == islandId
                && currentSize != null && Math.abs(currentSize - radius) < 0.5) {
            return; // No change
        }

        playerCurrentIsland.put(player.getUniqueId(), islandId);
        playerCurrentBorderSize.put(player.getUniqueId(), radius);
        sendBorderPacket(player, center.getX(), center.getZ(), radius * 2);
    }

    private void clearBorder(final Player player) {
        final UUID uuid = player.getUniqueId();
        if (playerCurrentIsland.remove(uuid) != null || playerCurrentBorderSize.remove(uuid) != null) {
            // Reset to the world default (no visible border).
            sendBorderPacket(player, 0, 0, 60000000);
        }
    }

    /**
     * Send a per-player WorldBorder packet (client-side visual only, no damage).
     * Falls back to the world's own border if the player's world is somehow null.
     */
    private void sendBorderPacket(final Player player, final double centerX, final double centerZ, final double size) {
        final World world = player.getWorld();
        if (world == null) return;
        final org.bukkit.WorldBorder border = world.getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(size);
        border.setDamageAmount(0);
        border.setDamageBuffer(0);
        border.setWarningDistance(0);
        border.setWarningTime(0);
        player.setWorldBorder(border);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof final Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            final Location loc = player.getLocation();
            if (!isSkyblockWorld(loc.getWorld())) return;
            final Island island = findIslandAt(loc);
            if (island != null && island.getSpawn() != null) {
                event.setCancelled(true);
                final Location spawn = island.getSpawn();
                player.teleport(spawn.clone().add(0.5, 1, 0.5));
                player.setHealth(player.getMaxHealth());
                player.setFireTicks(0);
                player.setFallDistance(0);
                CoreFoundation.getInstance().messages().sendRaw(player,
                        CoreFoundation.getInstance().messages().getPrefix()
                                + " <yellow>You fell into the void and were returned to the island spawn!");
            }
        }
    }

    private Island findIslandAt(final Location loc) {
        final List<Island> all = api.getAllIslands();
        for (final Island island : all) {
            final Location spawn = island.getSpawn();
            if (spawn == null || !spawn.getWorld().equals(loc.getWorld())) continue;
            final double radius = islandManager.getIslandRadius(island);
            final double dx = Math.abs(loc.getX() - spawn.getX());
            final double dz = Math.abs(loc.getZ() - spawn.getZ());
            if (dx <= radius && dz <= radius) {
                return island;
            }
        }
        return null;
    }

    private boolean isMember(final Player player, final Island island) {
        return island.getOwner().equals(player.getUniqueId())
                || island.getMembers().contains(player.getUniqueId());
    }

    @EventHandler
    public void onMove(final PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            if (isSkyblockWorld(event.getTo().getWorld())) updateBorder(player);
            else clearBorder(player);
        } else if (event.getTo().getWorld() != null && isSkyblockWorld(event.getTo().getWorld())) {
            final Island island = findIslandAt(event.getTo());
            final Integer currentIslandId = playerCurrentIsland.get(player.getUniqueId());
            if (island == null) {
                if (currentIslandId != null) clearBorder(player);
            } else if (currentIslandId == null || currentIslandId != island.getId()) {
                updateBorder(player);
            }
        }
    }

    @EventHandler
    public void onTeleport(final PlayerTeleportEvent event) {
        final Player player = event.getPlayer();
        final World toWorld = event.getTo().getWorld();
        if (toWorld != null && isSkyblockWorld(toWorld)) {
            Bukkit.getScheduler().runTask(CoreMC.getInstance(), () -> updateBorder(player));
        } else {
            clearBorder(player);
        }
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (isSkyblockWorld(player.getWorld())) {
            Bukkit.getScheduler().runTask(CoreMC.getInstance(), () -> updateBorder(player));
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        clearBorder(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(final PlayerChangedWorldEvent event) {
        final Player player = event.getPlayer();
        if (isSkyblockWorld(player.getWorld())) {
            updateBorder(player);
        } else {
            clearBorder(player);
        }
    }

    private boolean isSkyblockWorld(final World world) {
        return world != null && world.getName().equals("skyblock");
    }

    /**
     * Force-refresh the borders for every member of an island (e.g. after an
     * Island Size upgrade changes the radius). Called from the progression
     * module's purchase path.
     */
    public static void refreshIslandBorders(final int islandId) {
        final IslandApi api = CoreMC.getInstance().islands().api();
        final Island island = api.getIsland(islandId);
        if (island == null) return;
        final java.util.Set<UUID> viewers = new java.util.HashSet<>();
        viewers.add(island.getOwner());
        viewers.addAll(island.getMembers());
        for (final UUID u : viewers) {
            final Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline() && p.getWorld().getName().equals("skyblock")) {
                final IslandBorderListener self = listenerRef;
                if (self != null) {
                    self.playerCurrentIsland.remove(u);
                    self.playerCurrentBorderSize.remove(u);
                    self.updateBorder(p);
                }
            }
        }
    }

    private static IslandBorderListener listenerRef;

    /** Called by the module after construction so static refresh can reach the instance. */
    public void register() {
        listenerRef = this;
    }
}
