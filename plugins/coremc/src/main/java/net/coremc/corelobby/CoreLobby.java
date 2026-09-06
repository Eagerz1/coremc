package net.coremc.corelobby;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * CoreLobby — owns the server's main lobby world.
 *
 * <ul>
 *   <li>The {@code lobby} world is generated as a void (see {@link VoidGenerator}).</li>
 *   <li>A small invisible barrier platform is placed at spawn so players have footing.</li>
 *   <li>Joining, respawning and {@code /spawn} (or {@code /lobby}) route players to the
 *       lobby spawn — the lobby is the server's main world.</li>
 *   <li>Falling into the void teleports the player straight back to the lobby spawn.</li>
 * </ul>
 *
 * This is intentionally decoupled from the Skyblock/CoreMC plugin so the lobby
 * behaviour can evolve without touching unrelated systems.
 */
public final class CoreLobby extends JavaPlugin implements Listener {

    public static final String LOBBY_WORLD = "lobby";
    private static final int PLATFORM_Y = -21;
    private static final int PLATFORM_HALF = 4; // 9x9 platform

    private Location spawn;

    @Override
    public void onEnable() {
        // If level-name is set to "lobby", the server may have already generated a
        // non-void world before this plugin loaded. Regenerate it as a void once
        // (guarded by a marker file) so the lobby is genuinely empty.
        final File voidMarker = new File(getDataFolder(), "void-initialized");
        World world = Bukkit.getWorld(LOBBY_WORLD);

        if (world == null) {
            world = Bukkit.createWorld(new WorldCreator(LOBBY_WORLD).generator(new VoidGenerator()));
        } else if (!voidMarker.exists()) {
            try {
                Bukkit.unloadWorld(world, false);
                world = Bukkit.createWorld(new WorldCreator(LOBBY_WORLD).generator(new VoidGenerator()));
            } catch (final Exception ex) {
                getLogger().warning("Could not regenerate lobby as void: " + ex.getMessage()
                        + " — keeping the existing world; delete the 'lobby' folder for a fresh void world.");
                world = Bukkit.getWorld(LOBBY_WORLD);
            }
        }

        try {
            voidMarker.getParentFile().mkdirs();
            voidMarker.createNewFile();
        } catch (final IOException ignored) {
            // best-effort marker; regeneration is harmless to retry
        }

        // Build the platform + spawn after the world is fully loaded.
        Bukkit.getScheduler().runTaskLater(this, this::setupLobby, 1L);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("CoreLobby enabled. Main lobby world = '" + LOBBY_WORLD + "'.");
    }

    private void setupLobby() {
        final World w = Bukkit.getWorld(LOBBY_WORLD);
        if (w == null) {
            return;
        }
        // Invisible, unbreakable footing so the void world is still standable.
        for (int x = -PLATFORM_HALF; x <= PLATFORM_HALF; x++) {
            for (int z = -PLATFORM_HALF; z <= PLATFORM_HALF; z++) {
                if (w.getBlockAt(x, PLATFORM_Y, z).getType().isAir()) {
                    w.getBlockAt(x, PLATFORM_Y, z).setType(Material.BARRIER);
                }
            }
        }
        // Keep spawn loaded and centred on the platform.
        w.setSpawnLocation(0, PLATFORM_Y + 2, 0);
        spawn = new Location(w, 0.5, PLATFORM_Y + 2.0, 0.5, 0f, 0f);
    }

    /** Resolved lobby spawn (recomputed lazily if not yet set). */
    private Location lobbySpawn() {
        final World w = Bukkit.getWorld(LOBBY_WORLD);
        if (w == null) {
            return null;
        }
        if (spawn == null) {
            spawn = new Location(w, 0.5, PLATFORM_Y + 2.0, 0.5, 0f, 0f);
        }
        return spawn;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent e) {
        final Location s = lobbySpawn();
        if (s != null) {
            e.getPlayer().teleportAsync(s);
        }
    }

    @EventHandler
    public void onRespawn(final PlayerRespawnEvent e) {
        final Location s = lobbySpawn();
        if (s != null) {
            e.setRespawnLocation(s);
        }
    }

    @EventHandler
    public void onVoidDamage(final EntityDamageEvent e) {
        if (!(e.getEntity() instanceof final Player p)) {
            return;
        }
        if (e.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        final Location s = lobbySpawn();
        if (s != null) {
            e.setCancelled(true); // don't kill — bounce them back to lobby spawn
            p.teleportAsync(s);
        }
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd,
                             final String label, final String[] args) {
        final String name = cmd.getName().toLowerCase();
        if (name.equals("lobby") || name.equals("spawn")) {
            if (sender instanceof final Player p) {
                final Location s = lobbySpawn();
                if (s != null) {
                    p.teleportAsync(s);
                }
            } else {
                sender.sendMessage("Lobby world: " + LOBBY_WORLD);
            }
            return true;
        }
        return false;
    }
}
