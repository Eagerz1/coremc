package net.coremc.skyblock.progression.command;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.skyblock.progression.ProgressionModule;
import net.coremc.skyblock.progression.token.TokenManager;
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

/**
 * /skytokens — view and manage Sky Token balances. Player-only view for self;
 * staff commands (add / remove / set / history) gated by core.skytokens.admin.
 * All changes are persisted with a full transaction record.
 */
public final class SkyTokensCommand implements CommandExecutor, TabCompleter {

    private final ProgressionModule prog;

    public SkyTokensCommand(final ProgressionModule prog) {
        this.prog = prog;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final TokenManager tokens = prog.tokens();

        if (args.length == 0) {
            if (!(sender instanceof final Player p)) {
                cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>Players only. Use /skytokens <player>.");
                return true;
            }
            cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <white>Your Sky Tokens: <aqua>"
                    + String.format(java.util.Locale.ROOT, "%,d", tokens.get(p.getUniqueId())));
            return true;
        }

        // /skytokens <player>
        if (args.length == 1 && !isStaffAction(args[0])) {
            final OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!sender.hasPermission("core.skytokens.admin") && !sender.isOp()
                    && !(sender instanceof Player p && p.getName().equalsIgnoreCase(args[0]))) {
                cf.messages().send(sender, "messages.no-permission");
                return true;
            }
            cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <white>Sky Tokens for "
                    + target.getName() + ": <aqua>"
                    + String.format(java.util.Locale.ROOT, "%,d", tokens.get(target.getUniqueId())));
            return true;
        }

        final String action = args[0].toLowerCase(java.util.Locale.ROOT);

        if (action.equals("history")) {
            final OfflinePlayer target = resolve(sender, args.length > 1 ? args[1] : null,
                    sender instanceof Player p ? p.getUniqueId() : null);
            if (target == null) return true;
            final List<String> hist = net.coremc.coremc.CoreMC.getInstance()
                    .playerDataManager().recentTransactions(target.getUniqueId(), 10);
            cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <white>Sky Token history for " + target.getName() + ":");
            if (hist.isEmpty()) {
                cf.messages().sendRaw(sender, "  <gray>• No transactions this session (ledger is in-memory).");
            } else {
                for (final String line : hist) {
                    cf.messages().sendRaw(sender, "  <gray>• " + line);
                }
            }
            return true;
        }

        if (!sender.hasPermission("core.skytokens.admin") && !sender.isOp()) {
            cf.messages().send(sender, "messages.no-permission");
            return true;
        }

        switch (action) {
            case "add", "remove", "set" -> {
                if (args.length < 4) {
                    cf.messages().sendRaw(sender, cf.messages().getPrefix()
                            + " <red>Usage: /skytokens " + action + " <player> <amount> <reason>");
                    return true;
                }
                final OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                final long amount;
                try {
                    amount = Long.parseLong(args[2]);
                } catch (final NumberFormatException e) {
                    cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>Invalid amount.");
                    return true;
                }
                if (amount <= 0) {
                    cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>Amount must be positive.");
                    return true;
                }
                final String reason = args.length > 3
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length))
                        : "Staff adjustment";
                final String executor = sender.getName();
                final long result;
                switch (action) {
                    case "add" -> result = tokens.add(target.getUniqueId(), amount, reason, executor);
                    case "remove" -> {
                        result = tokens.remove(target.getUniqueId(), amount, reason, executor);
                        if (result == -1) {
                            cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>"
                                    + target.getName() + " does not have " + amount + " tokens.");
                            return true;
                        }
                    }
                    default -> result = tokens.set(target.getUniqueId(), amount, reason, executor);
                }
                cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <green>" + action.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                        + action.substring(1) + " " + amount + " Sky Tokens for " + target.getName()
                        + " <gray>(new balance: <aqua>" + result + "<gray>)");
            }
            default -> cf.messages().sendRaw(sender, cf.messages().getPrefix()
                    + " <red>Usage: /skytokens [<player>|add|remove|set|history]");
        }
        return true;
    }

    private boolean isStaffAction(final String s) {
        return s.equalsIgnoreCase("add") || s.equalsIgnoreCase("remove")
                || s.equalsIgnoreCase("set") || s.equalsIgnoreCase("history");
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
        if (!sender.hasPermission("core.skytokens.admin") && !sender.isOp()
                && !(sender instanceof Player p && p.getName().equalsIgnoreCase(name))) {
            cf.messages().send(sender, "messages.no-permission");
            return null;
        }
        return Bukkit.getOfflinePlayer(name);
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (args.length == 1) {
            final List<String> opts = new ArrayList<>(List.of("history"));
            if (sender.hasPermission("core.skytokens.admin") || sender.isOp()) {
                opts.addAll(List.of("add", "remove", "set"));
            }
            return opts;
        }
        return new ArrayList<>();
    }
}
