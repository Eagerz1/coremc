package net.coremc.tags.command;

import net.coremc.foundation.CoreFoundation;
import net.coremc.tags.CoreTags;
import net.coremc.tags.api.CoreTagsApi;
import net.coremc.tags.gui.TagsGui;
import net.coremc.tags.manager.TagManager;
import net.coremc.tags.model.TagDef;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /tags — GUI, equip, and admin controls. */
public final class TagsCommand implements CommandExecutor, TabCompleter {

    private final CoreTags plugin;
    private final TagManager manager;
    private final CoreTagsApi api;

    public TagsCommand(final CoreTags plugin, final TagManager manager, final CoreTagsApi api) {
        this.plugin = plugin;
        this.manager = manager;
        this.api = api;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final CoreFoundation cf = CoreFoundation.getInstance();

        // ---- admin subcommands ----
        if (args.length >= 1 && isAdminAction(args[0])) {
            return admin(sender, args, cf);
        }

        if (!(sender instanceof final Player player)) {
            cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>Players only.");
            return true;
        }
        if (!player.hasPermission("core.tags.use") && !player.isOp()) {
            cf.messages().send(player, "messages.no-permission");
            return true;
        }

        // /tags <tag> — equip directly
        if (args.length >= 1) {
            final TagDef def = manager.getTag(args[0]);
            if (def == null) {
                cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>Unknown tag: " + args[0]);
                return true;
            }
            if (!api.hasTag(player, def.id())) {
                cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>You don't have the " + def.gradientMiniMessage() + " <red>tag.");
                return true;
            }
            api.setActiveTag(player, def.id());
            cf.messages().sendRaw(player, cf.messages().getPrefix() + " <green>Equipped " + def.gradientMiniMessage() + "<green>.");
            return true;
        }

        TagsGui.open(player);
        return true;
    }

    private boolean isAdminAction(final String a) {
        return a.equalsIgnoreCase("give") || a.equalsIgnoreCase("remove")
                || a.equalsIgnoreCase("list") || a.equalsIgnoreCase("info")
                || a.equalsIgnoreCase("reload");
    }

    private boolean admin(final CommandSender sender, final String[] args, final CoreFoundation cf) {
        final String action = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "reload" -> {
                if (!sender.hasPermission("core.tags.reload") && !sender.isOp()) {
                    cf.messages().send(sender, "messages.no-permission"); return true;
                }
                plugin.reloadConfig();
                plugin.saveResource("tags.yml", true);
                // reload catalogue by rebuilding the manager
                final net.coremc.tags.storage.TagStorage storage = new net.coremc.tags.storage.SqliteTagStorage(plugin);
                storage.init();
                final TagManager newMgr = new TagManager(plugin, storage);
                // copy live ownership caches across
                for (final Player p : Bukkit.getOnlinePlayers()) {
                    newMgr.ensureLoaded(p.getUniqueId());
                }
                plugin.manager().shutdown();
                setManager(newMgr);
                cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <green>Tags reloaded.");
                return true;
            }
            case "list" -> {
                if (!sender.hasPermission("core.tags.admin") && !sender.isOp()) {
                    cf.messages().send(sender, "messages.no-permission"); return true;
                }
                final StringBuilder sb = new StringBuilder("<white>Tags: ");
                for (final TagDef d : manager.catalogue()) {
                    sb.append(d.gradientMiniMessage()).append(" ");
                }
                cf.messages().sendRaw(sender, cf.messages().getPrefix() + " " + sb);
                return true;
            }
            case "info" -> {
                if (!sender.hasPermission("core.tags.admin") && !sender.isOp()) {
                    cf.messages().send(sender, "messages.no-permission"); return true;
                }
                if (args.length < 2) {
                    cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>Usage: /tags info <tag>");
                    return true;
                }
                final TagDef d = manager.getTag(args[1]);
                if (d == null) {
                    cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>Unknown tag.");
                    return true;
                }
                cf.messages().sendRaw(sender, cf.messages().getPrefix() + " " + d.gradientMiniMessage());
                cf.messages().sendRaw(sender, "  <gray>id: " + d.id());
                cf.messages().sendRaw(sender, "  <gray>source: " + d.source() + "  price: " + d.price());
                cf.messages().sendRaw(sender, "  <gray>how to get: " + (d.obtain().isEmpty() ? "n/a" : d.obtain()));
                return true;
            }
            case "give" -> {
                if (!sender.hasPermission("core.tags.give") && !sender.isOp()) {
                    cf.messages().send(sender, "messages.no-permission"); return true;
                }
                if (args.length < 3) {
                    cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>Usage: /tags give <player> <tag>");
                    return true;
                }
                final Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>Player not found.");
                    return true;
                }
                if (manager.getTag(args[2]) == null) {
                    cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>Unknown tag.");
                    return true;
                }
                api.unlockTag(target, args[2]);
                cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <green>Gave " + manager.getTag(args[2]).gradientMiniMessage()
                        + " <green>to " + target.getName() + ".");
                cf.messages().sendRaw(target, cf.messages().getPrefix() + " <green>You received the "
                        + manager.getTag(args[2]).gradientMiniMessage() + " <green>tag!");
                return true;
            }
            case "remove" -> {
                if (!sender.hasPermission("core.tags.remove") && !sender.isOp()) {
                    cf.messages().send(sender, "messages.no-permission"); return true;
                }
                if (args.length < 3) {
                    cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>Usage: /tags remove <player> <tag>");
                    return true;
                }
                final Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>Player not found.");
                    return true;
                }
                if (manager.getTag(args[2]) == null) {
                    cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <red>Unknown tag.");
                    return true;
                }
                api.removeTag(target, args[2]);
                cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <green>Removed " + manager.getTag(args[2]).gradientMiniMessage()
                        + " <green>from " + target.getName() + ".");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // replace the live manager (used by reload)
    private void setManager(final TagManager mgr) {
        try {
            final java.lang.reflect.Field f = CoreTags.class.getDeclaredField("manager");
            f.setAccessible(true);
            f.set(plugin, mgr);
            final java.lang.reflect.Field fa = CoreTags.class.getDeclaredField("api");
            fa.setAccessible(true);
            fa.set(plugin, new CoreTagsApi(plugin, mgr));
        } catch (final ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to swap manager on reload: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final List<String> out = new ArrayList<>();
        if (args.length == 1) {
            final List<String> opts = new ArrayList<>(List.of("give", "remove", "list", "info", "reload"));
            for (final TagDef d : manager.catalogue()) {
                opts.add(d.id());
            }
            for (final String o : opts) {
                if (o.toLowerCase().startsWith(args[0].toLowerCase())) {
                    out.add(o);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("remove")
                || args[0].equalsIgnoreCase("info"))) {
            for (final TagDef d : manager.catalogue()) {
                if (d.id().startsWith(args[1].toLowerCase())) {
                    out.add(d.id());
                }
            }
        }
        return out;
    }
}
