package net.coremc.corestaff.listener;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Keeps vanished / spectating staff invisible on tab and to other players
 * when they join, relogs, or when another player joins while they are hidden.
 *
 * <p>A player is hidden from everyone who lacks {@code corestaff.vanish}
 * (and is not an op). Everyone with that permission node or who is opped
 * can still see them — so staff see other staff, but regular players see
 * neither vanished nor spectating members.</p>
 */
public final class StaffListener implements Listener {

    private final JavaPlugin plugin;
    private final Set<UUID> vanished;
    private final Set<UUID> spectating;

    public StaffListener(final JavaPlugin plugin, final Set<UUID> vanished, final Set<UUID> spectating) {
        this.plugin = plugin;
        this.vanished = vanished;
        this.spectating = spectating;
    }

    /** Returns true if the given player can see vanished/spectating staff. */
    private static boolean canSeeHidden(final Player p) {
        return p.hasPermission("corestaff.vanish") || p.isOp();
    }

    /** When any player joins, make sure all currently-hidden staff stay hidden from them. */
    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player joining = event.getPlayer();
        // A vanished/spectating player should not appear on the tab of a freshly joined player.
        for (final UUID uuid : vanished) {
            final Player hidden = Bukkit.getPlayer(uuid);
            if (hidden != null && hidden.isOnline() && !canSeeHidden(joining)) {
                joining.hidePlayer(plugin, hidden);
                // Also remove from tab list for players who can't see them.
                joining.hidePlayer(plugin, hidden);
            }
        }
    }

    /** When a vanished/spectating player rejoins, re-hide them from everyone. */
    @EventHandler
    public void onRejoin(final PlayerJoinEvent event) {
        final Player staff = event.getPlayer();
        if (vanished.contains(staff.getUniqueId())) {
            for (final Player p : Bukkit.getOnlinePlayers()) {
                if (!canSeeHidden(p) && p.getPlayer() != staff) {
                    p.hidePlayer(plugin, staff);
                    // Remove from tab list for viewers who can't see them.
                    p.hidePlayer(plugin, staff);
                }
            }
        }
    }

    /** When a vanished/spectating player quits, clear them from the sets. */
    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        vanished.remove(uuid);
        spectating.remove(uuid);
    }
}