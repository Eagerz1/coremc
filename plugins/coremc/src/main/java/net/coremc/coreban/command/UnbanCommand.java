package net.coremc.coreban.command;
import org.bukkit.plugin.java.JavaPlugin;


import net.coremc.coreban.model.Punishment;
import net.coremc.coreban.model.PunishmentType;
import net.coremc.coreban.storage.PunishmentStorage;
import net.coremc.foundation.CoreFoundation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.UUID;

/**
 * /unban <player>
 */
public final class UnbanCommand implements org.bukkit.command.CommandExecutor {

    private final JavaPlugin plugin;
    private final PunishmentStorage storage;

    public UnbanCommand(final JavaPlugin plugin, final PunishmentStorage storage) {
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
            sender.sendMessage("/unban <player>");
            return true;
        }
        final var target = CommandUtil.resolve(args[0]);
        final org.bukkit.BanEntry entry = org.bukkit.Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                .getBanEntry(target.getName());
        if (entry != null) {
            entry.remove();
        }
        final Punishment active = storage.getActive(target.getUniqueId(), PunishmentType.BAN);
        if (active != null) {
            storage.setExpired(active.id(), System.currentTimeMillis() / 1000);
        }
        CoreFoundation.getInstance().messages().sendRaw(sender,
                CoreFoundation.getInstance().messages().getPrefix() + " <green>Unbanned " + target.getName());
        return true;
    }
}
