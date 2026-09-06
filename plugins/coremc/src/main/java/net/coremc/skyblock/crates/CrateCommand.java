package net.coremc.skyblock.crates;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.skyblock.crates.config.LootboxConfig;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Management / give commands for the crate system.
 *
 * <p>/crate gui — open the crate browser. /crate give &lt;player&gt; &lt;key|lootbox&gt; &lt;amount&gt;
 * grants a key or lootbox. /crate reload reloads config. Permission-gated via
 * {@code core.crates.admin}.</p>
 */
public final class CrateCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final CrateModule module;

    public CrateCommand(final JavaPlugin plugin, final CrateModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) {
                module.openGui(p);
            } else {
                sender.sendMessage("Usage: /crate give <player> <key|lootbox> <amount> | /crate reload");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "gui" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                module.openGui(p);
                return true;
            }
            case "give" -> {
                if (!sender.hasPermission("core.crates.admin")) {
                    noPerm(sender);
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage("Usage: /crate give <player> <key|lootbox|[boxId]> <amount>");
                    return true;
                }
                final Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("Player not found.");
                    return true;
                }
                final int amount = safeInt(args[3], 1);
                if (args[2].equalsIgnoreCase("lootbox")) {
                    module.giveLootbox(target, amount);
                    sender.sendMessage("Gave " + amount + " Lootbox to " + target.getName() + ".");
                } else if (module.lootbox().get(args[2]) != null) {
                    module.giveLootbox(target, args[2], amount);
                    sender.sendMessage("Gave " + amount + " " + args[2] + " Lootbox to " + target.getName() + ".");
                } else {
                    final KeyId id = KeyId.byId(args[2]);
                    if (id == null) {
                        sender.sendMessage("Unknown key: " + args[2]);
                        return true;
                    }
                    module.giveKey(target, id, amount);
                    sender.sendMessage("Gave " + amount + " " + id.defaultName() + " to " + target.getName() + ".");
                }
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("core.crates.admin")) {
                    noPerm(sender);
                    return true;
                }
                module.reload();
                sender.sendMessage("Crate configuration reloaded.");
                return true;
            }
            case "crate" -> {
                if (!sender.hasPermission("core.crates.admin")) {
                    noPerm(sender);
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage("Usage: /crate crate <player> <key> <amount>");
                    return true;
                }
                final Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("Player not found.");
                    return true;
                }
                final int amount = safeInt(args[3], 1);
                final KeyId id = KeyId.byId(args[2]);
                if (id == null) {
                    sender.sendMessage("Unknown key: " + args[2]);
                    return true;
                }
                module.giveCrate(target, id, amount);
                sender.sendMessage("Gave " + amount + " " + id.defaultName().replace(" Key", " Crate")
                        + " to " + target.getName() + ".");
                return true;
            }
            case "open" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (args.length < 2) {
                    p.closeInventory();
                    module.lootbox().openLobby(p);
                    return true;
                }
                final LootboxConfig cfg = module.lootbox().get(args[1]);
                if (cfg == null) {
                    sender.sendMessage("Unknown lootbox: " + args[1]);
                    return true;
                }
                module.lootbox().openById(p, cfg);
                return true;
            }
            case "preview" -> {
                if (!sender.hasPermission("core.crates.admin") && !(sender instanceof Player)) {
                    noPerm(sender);
                    return true;
                }
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("Usage: /lootbox preview <beta|sotw|summer>");
                    return true;
                }
                final LootboxConfig cfg = module.lootbox().get(args[1]);
                if (cfg == null) {
                    sender.sendMessage("Unknown lootbox: " + args[1]);
                    return true;
                }
                module.lootbox().openPreview(p, cfg);
                return true;
            }
            case "list" -> {
                final StringBuilder sb = new StringBuilder("Lootboxes: ");
                for (final String id : module.lootbox().ids()) sb.append(id).append(" ");
                sender.sendMessage(sb.toString().trim());
                return true;
            }
            default -> {
                sender.sendMessage("Usage: /lootbox [gui|open <box>|preview <box>|list] | /crate give <player> <box|key> <amount> | /crate reload");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!sender.hasPermission("core.crates.admin") && args.length > 0
                && !args[0].equalsIgnoreCase("gui")) {
            return new ArrayList<>();
        }
        if (args.length == 1) {
            return filter(List.of("gui", "give", "crate", "reload", "open", "preview", "list"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            final List<String> names = new ArrayList<>();
            for (final Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("open")) {
            return filter(new ArrayList<>(module.lootbox().ids()), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("crate")) {
            final List<String> names = new ArrayList<>();
            for (final Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            final List<String> keys = new ArrayList<>();
            keys.add("lootbox");
            for (final KeyId k : KeyId.values()) keys.add(k.id());
            for (final String s : module.lootbox().ids()) keys.add(s);
            return filter(keys, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("crate")) {
            final List<String> keys = new ArrayList<>();
            for (final KeyId k : KeyId.values()) keys.add(k.id());
            return filter(keys, args[2]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(final List<String> in, final String prefix) {
        final String p = prefix.toLowerCase();
        final List<String> out = new ArrayList<>();
        for (final String s : in) if (s.toLowerCase().startsWith(p)) out.add(s);
        return out;
    }

    private void noPerm(final CommandSender sender) {
        sender.sendMessage(CoreFoundation.getInstance().messages()
                .parse("<prefix> <red>You do not have permission to do that.</red>",
                        plugin.getConfig().getString("prefix")));
    }

    private int safeInt(final String s, final int def) {
        try { return Math.max(1, Integer.parseInt(s)); } catch (final NumberFormatException e) { return def; }
    }
}
