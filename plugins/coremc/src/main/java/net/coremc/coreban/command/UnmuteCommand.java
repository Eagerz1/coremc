package net.coremc.coreban.command;
import net.coremc.coremc.CoreMC;
import org.bukkit.plugin.java.JavaPlugin;


import net.coremc.coreban.model.Punishment;
import net.coremc.coreban.model.PunishmentType;
import net.coremc.coreban.storage.PunishmentStorage;
import net.coremc.foundation.CoreFoundation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 * /unmute <player>
 */
public final class UnmuteCommand implements org.bukkit.command.CommandExecutor {

    private final JavaPlugin plugin;
    private final PunishmentStorage storage;

    public UnmuteCommand(final JavaPlugin plugin, final PunishmentStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!sender.hasPermission("coreban.unban")) {
            CoreFoundation.getInstance().messages().send(sender, "messages.no-permission");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("/unmute <player>");
            return true;
        }
        final var target = CommandUtil.resolve(args[0]);
        CoreMC.getInstance().ban().muted().remove(target.getUniqueId());
        final Punishment active = storage.getActive(target.getUniqueId(), PunishmentType.MUTE);
        if (active != null) {
            storage.setExpired(active.id(), System.currentTimeMillis() / 1000);
        }
        CoreFoundation.getInstance().messages().sendRaw(sender,
                CoreFoundation.getInstance().messages().getPrefix() + " <green>Unmuted " + target.getName());
        return true;
    }
}
