package net.coremc.skyblock.companion;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
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
import java.util.Locale;

/**
 * /companion — open the Companion GUI.
 * /companion merge <id>   — merge one tier of a companion you own.
 * /companion equip <id>   — equip the highest owned rarity of a companion.
 * /companion unequip      — unequip the current companion.
 * /companion give <player> <id> [rarity] — admin: grant a companion.
 */
public final class CompanionCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final CompanionManager manager;
    private final CompanionGui gui;

    public CompanionCommand(final JavaPlugin plugin, final CompanionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = new CompanionGui(plugin, manager);
    }

    public CompanionGui gui() { return gui; }

    private String prefix() {
        return plugin.getConfig().getString("prefix");
    }

    private void msg(final CommandSender s, final String mini) {
        s.sendMessage(CoreFoundation.getInstance().messages().parse("<prefix> " + mini, prefix()));
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                msg(sender, "<red>Players only.</red>");
                return true;
            }
            gui.open(p);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "merge" -> {
                if (!(sender instanceof Player p)) { msg(sender, "<red>Players only.</red>"); return true; }
                if (args.length < 2) { msg(p, "<red>Usage: /companion merge <companion></red>"); return true; }
                final String id = args[1].toLowerCase(Locale.ROOT);
                if (!manager.exists(id)) { msg(p, "<red>Unknown companion: " + id + "</red>"); return true; }
                final CompanionManager.Rarity res = manager.merge(p, id);
                if (res == null) {
                    msg(p, "<red>You don't have enough " + manager.displayName(id)
                            + " to merge (need 6 Common, 4 Rare, or 3 Epic).</red>");
                }
                return true;
            }
            case "equip" -> {
                if (!(sender instanceof Player p)) { msg(sender, "<red>Players only.</red>"); return true; }
                if (args.length < 2) { msg(p, "<red>Usage: /companion equip <companion></red>"); return true; }
                final String id = args[1].toLowerCase(Locale.ROOT);
                if (!manager.exists(id)) { msg(p, "<red>Unknown companion: " + id + "</red>"); return true; }
                CompanionManager.Rarity best = null;
                for (final CompanionManager.Rarity r : manager.rarities()) {
                    if (manager.ownedCount(p.getUniqueId(), id, r) > 0) best = r;
                }
                if (best == null) { msg(p, "<red>You do not own " + manager.displayName(id) + ".</red>"); return true; }
                manager.equip(p, id, best);
                return true;
            }
            case "unequip" -> {
                if (!(sender instanceof Player p)) { msg(sender, "<red>Players only.</red>"); return true; }
                manager.unequip(p);
                return true;
            }
            case "give" -> {
                if (!sender.hasPermission("core.companions.admin")) {
                    msg(sender, "<red>You do not have permission to do that.</red>");
                    return true;
                }
                if (args.length < 3) { msg(sender, "<red>Usage: /companion give <player> <companion> [rarity]</red>"); return true; }
                final Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { msg(sender, "<red>Player not found.</red>"); return true; }
                final String id = args[2].toLowerCase(Locale.ROOT);
                if (!manager.exists(id)) { msg(sender, "<red>Unknown companion: " + id + "</red>"); return true; }
                final CompanionManager.Rarity r = args.length >= 4
                        ? parseRarity(args[3]) : CompanionManager.Rarity.COMMON;
                if (r == null) { msg(sender, "<red>Unknown rarity: " + args[3] + "</red>"); return true; }
                manager.grant(target, id, r, 1);
                msg(sender, "<green>Gave " + target.getName() + " " + manager.displayName(id) + " (" + r.name() + ").</green>");
                return true;
            }
            default -> {
                msg(sender, "<red>Usage: /companion [merge|equip|unequip|give]</red>");
                return true;
            }
        }
    }

    private CompanionManager.Rarity parseRarity(final String s) {
        try {
            return CompanionManager.Rarity.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (args.length == 1) {
            return filter(List.of("merge", "equip", "unequip", "give"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("merge") || args[0].equalsIgnoreCase("equip"))) {
            return filter(manager.companionIds(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            final List<String> names = new ArrayList<>();
            for (final Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return filter(Arrays.stream(CompanionManager.Rarity.values()).map(Enum::name).toList(), args[3]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(final List<String> in, final String prefix) {
        final String p = prefix.toLowerCase(Locale.ROOT);
        final List<String> out = new ArrayList<>();
        for (final String s : in) if (s.toLowerCase(Locale.ROOT).startsWith(p)) out.add(s);
        return out;
    }
}
