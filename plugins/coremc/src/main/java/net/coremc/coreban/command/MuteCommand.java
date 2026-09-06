package net.coremc.coreban.command;
import net.coremc.coremc.CoreMC;
import org.bukkit.plugin.java.JavaPlugin;


import net.coremc.coreban.model.Punishment;
import net.coremc.coreban.model.PunishmentType;
import net.coremc.coreban.storage.PunishmentStorage;
import net.coremc.foundation.CoreFoundation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /mute <player> <tier> <reason>
 */
public final class MuteCommand implements org.bukkit.command.CommandExecutor {

    private final JavaPlugin plugin;
    private final PunishmentStorage storage;

    public MuteCommand(final JavaPlugin plugin, final PunishmentStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage("/mute <player> <tier> <reason>");
            return true;
        }
        final int tier;
        try {
            tier = Integer.parseInt(args[1]);
        } catch (final NumberFormatException e) {
            sender.sendMessage("Tier must be a number 1-5.");
            return true;
        }
        if (sender instanceof final Player p && !CommandUtil.hasTierPerm(p, PunishmentType.MUTE, tier)) {
            CoreFoundation.getInstance().messages().send(sender, "messages.no-permission");
            return true;
        }
        final var target = CommandUtil.resolve(args[0]);
        final String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        final long now = System.currentTimeMillis() / 1000;
        final Punishment p = CommandUtil.apply(plugin, PunishmentType.MUTE, target.getUniqueId(), tier, reason,
                sender.getName(), now);
        if (target.isOnline() && target.getPlayer() != null) {
            CoreMC.getInstance().ban().muted().put(target.getUniqueId(), p);
        }
        CommandUtil.announce(plugin, p);
        return true;
    }
}
