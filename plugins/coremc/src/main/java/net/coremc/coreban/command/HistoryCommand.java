package net.coremc.coreban.command;
import org.bukkit.plugin.java.JavaPlugin;


import net.coremc.coreban.TierLadder;
import net.coremc.coreban.model.Punishment;
import net.coremc.coreban.storage.PunishmentStorage;
import net.coremc.foundation.CoreFoundation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * /history <player>
 */
public final class HistoryCommand implements org.bukkit.command.CommandExecutor {

    private final JavaPlugin plugin;
    private final PunishmentStorage storage;

    public HistoryCommand(final JavaPlugin plugin, final PunishmentStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!sender.hasPermission("coreban.history")) {
            CoreFoundation.getInstance().messages().send(sender, "messages.no-permission");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("/history <player>");
            return true;
        }
        final var target = CommandUtil.resolve(args[0]);
        final List<Punishment> history = storage.history(target.getUniqueId());
        final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        CoreFoundation.getInstance().messages().sendRaw(sender,
                CoreFoundation.getInstance().messages().getPrefix()
                + " <yellow>History for " + target.getName() + " (" + history.size() + ")");
        for (final Punishment p : history) {
            final String expired = p.durationSeconds() == -1 ? "permanent"
                    : fmt.format(new Date(p.issuedAt() * 1000));
            CoreFoundation.getInstance().messages().sendRaw(sender,
                    "  <gray>#" + p.id() + " <white>" + p.type() + " T" + p.tier()
                    + " (#" + p.offenceNumber() + ") " + TierLadder.formatDuration(p.durationSeconds())
                    + " <gray>- " + p.reason() + " by " + p.staff() + " @ " + expired);
        }
        return true;
    }
}
