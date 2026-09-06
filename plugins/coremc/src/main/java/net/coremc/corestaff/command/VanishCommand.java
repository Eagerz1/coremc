package net.coremc.corestaff.command;
import net.coremc.coremc.CoreMC;
import org.bukkit.plugin.java.JavaPlugin;


import net.coremc.foundation.CoreFoundation;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * /vanish - toggle staff vanish (hides from other players).
 *
 * <p>Oped players and those with {@code corestaff.vanish} can still see
 * vanished staff in tab and in-world.</p>
 */
public final class VanishCommand implements org.bukkit.command.CommandExecutor {

    private final JavaPlugin plugin;
    private final Set<UUID> vanished;

    public VanishCommand(final JavaPlugin plugin, final Set<UUID> vanished) {
        this.plugin = plugin;
        this.vanished = vanished;
    }

    /** Returns true if the given player can see vanished staff. */
    private static boolean canSeeVanished(final Player p) {
        return p.hasPermission("corestaff.vanish") || p.isOp();
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!sender.hasPermission("corestaff.vanish")) {
            CoreFoundation.getInstance().messages().send(sender, "messages.no-permission");
            return true;
        }
        if (!(sender instanceof final Player staff)) {
            sender.sendMessage("Players only.");
            return true;
        }
        final UUID uuid = staff.getUniqueId();
        if (vanished.contains(uuid)) {
            vanished.remove(uuid);
            for (final Player p : Bukkit.getOnlinePlayers()) {
                p.showPlayer(plugin, staff);
            }
            CoreFoundation.getInstance().messages().sendRaw(staff, CoreFoundation.getInstance().messages().getPrefix()
                    + " <green>Vanish OFF.");
        } else {
            vanished.add(uuid);
            for (final Player p : Bukkit.getOnlinePlayers()) {
                if (!canSeeVanished(p) && p.getPlayer() != staff) {
                    p.hidePlayer(plugin, staff);
                }
            }
            CoreFoundation.getInstance().messages().sendRaw(staff, CoreFoundation.getInstance().messages().getPrefix()
                    + " <green>Vanish ON.");
        }
        CoreMC.getInstance().staff().log(staff.getName() + " toggled vanish (" + vanished.contains(uuid) + ").");
        return true;
    }
}