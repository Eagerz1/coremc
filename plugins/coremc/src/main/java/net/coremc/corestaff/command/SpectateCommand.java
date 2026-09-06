package net.coremc.corestaff.command;
import net.coremc.coremc.CoreMC;
import org.bukkit.plugin.java.JavaPlugin;


import net.coremc.foundation.CoreFoundation;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /spectate <player> — vanish and teleport behind the target as a spectator.
 * /spectate (no args) — stop spectating and return to your previous gamemode
 * and location.
 *
 * <p>Oped/staff players with {@code corestaff.vanish} (or ops) can see each other
 * even while vanished or spectating. Vanish and spectate share a single
 * {@code vanished} set so the toggle logic is identical.</p>
 */
public final class SpectateCommand implements org.bukkit.command.CommandExecutor {

    private final JavaPlugin plugin;
    private final Set<UUID> vanished;
    private final Set<UUID> spectating;
    /** Snapshot of each spectating player's original gamemode so we can restore it. */
    private final Map<UUID, GameMode> priorGamemode;
    /** Snapshot of each spectating player's original location so we can restore it. */
    private final Map<UUID, Location> priorLocation;

    public SpectateCommand(final JavaPlugin plugin, final Set<UUID> vanished,
                           final Set<UUID> spectating,
                           final Map<UUID, GameMode> priorGamemode,
                           final Map<UUID, Location> priorLocation) {
        this.plugin = plugin;
        this.vanished = vanished;
        this.spectating = spectating;
        this.priorGamemode = priorGamemode;
        this.priorLocation = priorLocation;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!sender.hasPermission("corestaff.spectate")) {
            CoreFoundation.getInstance().messages().send(sender, "messages.no-permission");
            return true;
        }
        if (!(sender instanceof final Player staff)) {
            sender.sendMessage("Players only.");
            return true;
        }
        final UUID uuid = staff.getUniqueId();

        // No args => stop spectating (if currently spectating).
        if (args.length < 1) {
            if (!spectating.contains(uuid)) {
                CoreFoundation.getInstance().messages().sendRaw(staff,
                        CoreFoundation.getInstance().messages().getPrefix()
                                + " <red>You are not currently spectating.");
                return true;
            }
            stopSpectating(staff);
            CoreFoundation.getInstance().messages().sendRaw(staff,
                    CoreFoundation.getInstance().messages().getPrefix()
                            + " <green>Spectate OFF. Restored gamemode and location.");
            return true;
        }

        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            CoreFoundation.getInstance().messages().sendRaw(staff,
                    CoreFoundation.getInstance().messages().getPrefix()
                            + " <red>Player not found.");
            return true;
        }

        // If already spectating something, we'll move to the new target without
        // double-toggling vanish, but restore the original location if this is a
        // fresh spectate (not already in spectating set).
        if (!spectating.contains(uuid)) {
            priorGamemode.put(uuid, staff.getGameMode());
            priorLocation.put(uuid, staff.getLocation().clone());
            spectating.add(uuid);
        }

        // Vanish: hide from players who can't see vanished staff (non-ops without perm).
        vanished.add(uuid);
        for (final Player p : Bukkit.getOnlinePlayers()) {
            if (!canSeeVanished(p) && p.getPlayer() != staff) {
                p.hidePlayer(plugin, staff);
            }
        }
        // The staff member should still be able to see the player they are spectating.
        target.showPlayer(plugin, staff);

        // Teleport behind the target into spectator mode.
        final Location behind = target.getLocation().add(target.getLocation().getDirection().multiply(-3));
        staff.teleport(behind);
        staff.setGameMode(GameMode.SPECTATOR);
        CoreMC.getInstance().staff().log(staff.getName() + " spectating " + target.getName());
        CoreFoundation.getInstance().messages().sendRaw(staff,
                CoreFoundation.getInstance().messages().getPrefix()
                        + " <green>Now spectating " + target.getName() + ".");
        CoreFoundation.getInstance().messages().sendRaw(staff,
                CoreFoundation.getInstance().messages().getPrefix()
                        + " <gray>Type /spectate to stop.");
        return true;
    }

    /** Returns true if the given player has permission or is opped and can see vanished/spectating staff. */
    private static boolean canSeeVanished(final Player p) {
        return p.hasPermission("corestaff.vanish") || p.isOp();
    }

    /** Restores the player to their prior gamemode and location and reveals them to the world. */
    private void stopSpectating(final Player staff) {
        final UUID uuid = staff.getUniqueId();
        spectating.remove(uuid);
        vanished.remove(uuid);
        for (final Player p : Bukkit.getOnlinePlayers()) {
            p.showPlayer(plugin, staff);
        }
        final GameMode mode = priorGamemode.remove(uuid);
        if (mode != null) {
            staff.setGameMode(mode);
        } else {
            staff.setGameMode(GameMode.SPECTATOR);
        }
        final Location loc = priorLocation.remove(uuid);
        if (loc != null) {
            staff.teleport(loc);
        }
        staff.setAllowFlight(false);
        staff.setFlying(false);
        CoreMC.getInstance().staff().log(staff.getName() + " stopped spectating.");
    }
}