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
 * /freeze <player> - prevent the target from moving until unfrozen.
 */
public final class FreezeCommand implements org.bukkit.command.CommandExecutor {

    private final JavaPlugin plugin;
    private final Set<UUID> frozen;

    public FreezeCommand(final JavaPlugin plugin, final Set<UUID> frozen) {
        this.plugin = plugin;
        this.frozen = frozen;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!sender.hasPermission("corestaff.freeze")) {
            CoreFoundation.getInstance().messages().send(sender, "messages.no-permission");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("/freeze <player>");
            return true;
        }
        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            CoreFoundation.getInstance().messages().sendRaw(sender, CoreFoundation.getInstance().messages().getPrefix()
                    + " <red>Player not found.");
            return true;
        }
        final UUID uuid = target.getUniqueId();
        if (frozen.contains(uuid)) {
            frozen.remove(uuid);
            CoreFoundation.getInstance().messages().sendRaw(target, CoreFoundation.getInstance().messages().getPrefix()
                    + " <green>You have been unfrozen.");
            CoreFoundation.getInstance().messages().sendRaw(sender, CoreFoundation.getInstance().messages().getPrefix()
                    + " <green>Unfroze " + target.getName());
        } else {
            frozen.add(uuid);
            CoreFoundation.getInstance().messages().sendRaw(target, CoreFoundation.getInstance().messages().getPrefix()
                    + " <red>You have been frozen by staff.");
            CoreFoundation.getInstance().messages().sendRaw(sender, CoreFoundation.getInstance().messages().getPrefix()
                    + " <green>Froze " + target.getName());
        }
        CoreMC.getInstance().staff().log(sender.getName() + " toggled freeze on " + target.getName());
        return true;
    }
}
