package net.coremc.store.command;

import net.coremc.foundation.CoreFoundation;
import net.coremc.store.CoreStore;
import net.coremc.store.credits.CreditsManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /credits — view, add, remove, set, history. */
public final class CreditsCommand implements CommandExecutor, TabCompleter {

    private final CoreStore plugin;
    private final CreditsManager credits;

    public CreditsCommand(final CoreStore plugin, final CreditsManager credits) {
        this.plugin = plugin;
        this.credits = credits;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        if (args.length == 0) {
            if (!(sender instanceof final Player p)) {
                cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>Players only.");
                return true;
            }
            if (!p.hasPermission("core.store.credits") && !p.isOp()) {
                cf.messages().send(p, "messages.no-permission");
                return true;
            }
            cf.messages().sendRaw(p, cf.messages().getPrefix() + " <white>Your Credits: <yellow>"
                    + String.format("%.0f", credits.getCredits(p.getUniqueId())));
            return true;
        }

        final String action = args[0].toLowerCase();
        if (action.equals("history")) {
            final OfflinePlayer target = resolve(sender, args.length > 1 ? args[1] : null, sender instanceof Player ? ((Player) sender).getUniqueId() : null);
            if (target == null) {
                return true;
            }
            final List<String> hist = credits.history(target.getUniqueId(), 10);
            cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <white>Credit history:");
            for (final String line : hist) {
                final String[] p = line.split("\\|");
                cf.messages().sendRaw(sender, "  <gray>• " + p[0] + " (" + p[1] + ")");
            }
            return true;
        }

        // admin actions require permission
        if (!sender.hasPermission("core.store.credits.admin") && !sender.isOp()) {
            cf.messages().send(sender, "messages.no-permission");
            return true;
        }
        if (args.length < 3) {
            cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>Usage: /credits <add|remove|set> <player> <amount> <reason>");
            return true;
        }
        final OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        final double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (final NumberFormatException e) {
            cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>Invalid amount.");
            return true;
        }
        final String reason = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "Staff adjustment";
        switch (action) {
            case "add" -> credits.addCredits(target.getUniqueId(), amount, reason, b ->
                cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <green>Added " + amount + " to " + target.getName()));
            case "remove" -> credits.removeCredits(target.getUniqueId(), amount, reason, b ->
                cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <green>Removed " + amount + " from " + target.getName()));
            case "set" -> {
                credits.setCredits(target.getUniqueId(), amount, reason);
                cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <green>Set " + target.getName() + " to " + amount);
            }
            default -> cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>Unknown action.");
        }
        return true;
    }

    private OfflinePlayer resolve(final CommandSender sender, final String name, final UUID self) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        if (name == null) {
            if (self == null) {
                cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>Specify a player.");
                return null;
            }
            return Bukkit.getOfflinePlayer(self);
        }
        if (!sender.hasPermission("core.store.credits.admin") && !sender.isOp() && !(sender instanceof Player p && p.getName().equalsIgnoreCase(name))) {
            cf.messages().send(sender, "messages.no-permission");
            return null;
        }
        return Bukkit.getOfflinePlayer(name);
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        return new ArrayList<>();
    }
}
