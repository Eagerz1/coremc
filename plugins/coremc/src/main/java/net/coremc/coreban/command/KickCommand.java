package net.coremc.coreban.command;
import org.bukkit.plugin.java.JavaPlugin;


import net.coremc.coreban.model.Punishment;
import net.coremc.coreban.model.PunishmentType;
import net.coremc.coreban.storage.PunishmentStorage;
import net.coremc.foundation.CoreFoundation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 * /kick <player> <reason>
 */
public final class KickCommand implements org.bukkit.command.CommandExecutor {

    private final JavaPlugin plugin;
    private final PunishmentStorage storage;

    public KickCommand(final JavaPlugin plugin, final PunishmentStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!sender.hasPermission("coreban.kick")) {
            CoreFoundation.getInstance().messages().send(sender, "messages.no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("/kick <player> <reason>");
            return true;
        }
        final var target = CommandUtil.resolve(args[0]);
        final String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        final long now = System.currentTimeMillis() / 1000;
        final Punishment p = CommandUtil.apply(plugin, PunishmentType.KICK, target.getUniqueId(), 0, reason,
                sender.getName(), now);
        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().kickPlayer(reason);
        }
        CommandUtil.announce(plugin, p);
        return true;
    }
}
