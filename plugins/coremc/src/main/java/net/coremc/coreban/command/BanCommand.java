package net.coremc.coreban.command;
import org.bukkit.plugin.java.JavaPlugin;


import net.coremc.coreban.model.Punishment;
import net.coremc.coreban.model.PunishmentType;
import net.coremc.coreban.storage.PunishmentStorage;
import net.coremc.foundation.CoreFoundation;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /ban <player> <tier> <reason>
 */
public final class BanCommand implements org.bukkit.command.CommandExecutor {

    private final JavaPlugin plugin;
    private final PunishmentStorage storage;

    public BanCommand(final JavaPlugin plugin, final PunishmentStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage("/ban <player> <tier> <reason>");
            return true;
        }
        final int tier;
        try {
            tier = Integer.parseInt(args[1]);
        } catch (final NumberFormatException e) {
            sender.sendMessage("Tier must be a number 1-5.");
            return true;
        }
        if (sender instanceof final Player p && !CommandUtil.hasTierPerm(p, PunishmentType.BAN, tier)) {
            CoreFoundation.getInstance().messages().send(sender, "messages.no-permission");
            return true;
        }
        final var target = CommandUtil.resolve(args[0]);
        final String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        final long now = System.currentTimeMillis() / 1000;
        final Punishment p = CommandUtil.apply(plugin, PunishmentType.BAN, target.getUniqueId(), tier, reason,
                sender.getName(), now);
        // Apply ban via Bukkit BanList.
        final var ban = Bukkit.getBanList(org.bukkit.BanList.Type.NAME);
        final org.bukkit.BanEntry newEntry = ban.addBan(target.getName(), reason, null, sender.getName());
        if (p.durationSeconds() > 0) {
            newEntry.setExpiration(java.util.Date.from(
                    java.time.Instant.ofEpochSecond(now + p.durationSeconds())));
        }
        newEntry.save();
        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().kickPlayer("Banned: " + reason);
        }
        CommandUtil.announce(plugin, p);
        return true;
    }
}
