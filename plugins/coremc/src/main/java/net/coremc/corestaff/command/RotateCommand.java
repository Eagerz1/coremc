package net.coremc.corestaff.command;
import net.coremc.coremc.CoreMC;
import org.bukkit.plugin.java.JavaPlugin;


import net.coremc.foundation.CoreFoundation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /rotate <player> - rotate the target's view direction by 180 degrees.
 */
public final class RotateCommand implements org.bukkit.command.CommandExecutor {

    private final JavaPlugin plugin;

    public RotateCommand(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!sender.hasPermission("corestaff.rotate")) {
            CoreFoundation.getInstance().messages().send(sender, "messages.no-permission");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("/rotate <player>");
            return true;
        }
        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            CoreFoundation.getInstance().messages().sendRaw(sender, CoreFoundation.getInstance().messages().getPrefix()
                    + " <red>Player not found.");
            return true;
        }
        final Location loc = target.getLocation();
        loc.setYaw(loc.getYaw() + 180);
        loc.setPitch(-loc.getPitch());
        target.teleport(loc);
        CoreMC.getInstance().staff().log(sender.getName() + " rotated " + target.getName());
        CoreFoundation.getInstance().messages().sendRaw(sender, CoreFoundation.getInstance().messages().getPrefix()
                + " <green>Rotated " + target.getName() + ".");
        return true;
    }
}
