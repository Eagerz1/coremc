package net.coremc.coreban.command;

import org.bukkit.plugin.java.JavaPlugin;

import net.coremc.coreban.model.Note;
import net.coremc.coreban.storage.NoteStorage;
import net.coremc.foundation.CoreFoundation;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.UUID;

/**
 * /note add <player> <note...>
 * /note remove <player> <noteID>
 */
public final class NoteCommand implements org.bukkit.command.CommandExecutor {

    private final JavaPlugin plugin;
    private final NoteStorage storage;

    public NoteCommand(final JavaPlugin plugin, final NoteStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (args.length < 1) {
            sender.sendMessage("/note <add|remove> <player> <note|noteID>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "add":
                return add(sender, args);
            case "remove":
            case "del":
            case "delete":
                return remove(sender, args);
            default:
                sender.sendMessage("/note <add|remove> <player> <note|noteID>");
                return true;
        }
    }

    private boolean add(final CommandSender sender, final String[] args) {
        if (sender instanceof final Player p && !canManage(p, "coreban.notes.add")) {
            CoreFoundation.getInstance().messages().send(sender, "messages.no-permission");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("/note add <player> <note>");
            return true;
        }
        final OfflinePlayer target = CommandUtil.resolve(args[1]);
        if (!found(target)) {
            CoreFoundation.getInstance().messages().send(sender, "messages.player-not-found");
            return true;
        }
        final String content = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        final UUID uuid = target.getUniqueId();
        final long now = System.currentTimeMillis() / 1000;
        final Note note = storage.insert(new Note(0, uuid, target.getName(), content,
                sender.getName(), now));
        CoreFoundation.getInstance().messages().sendRaw(sender,
                CoreFoundation.getInstance().messages().getPrefix()
                + " <green>Added note <white>#" + note.id() + " <green>for <white>"
                + target.getName() + "<green>: <white>" + content);
        return true;
    }

    private boolean remove(final CommandSender sender, final String[] args) {
        if (sender instanceof final Player p && !canManage(p, "coreban.notes.remove")) {
            CoreFoundation.getInstance().messages().send(sender, "messages.no-permission");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("/note remove <player> <noteID>");
            return true;
        }
        final int noteId;
        try {
            noteId = Integer.parseInt(args[2]);
        } catch (final NumberFormatException e) {
            sender.sendMessage("Note ID must be a number.");
            return true;
        }
        final OfflinePlayer target = CommandUtil.resolve(args[1]);
        if (!found(target)) {
            CoreFoundation.getInstance().messages().send(sender, "messages.player-not-found");
            return true;
        }
        final UUID uuid = target.getUniqueId();
        final Note existing = storage.get(uuid, noteId);
        if (existing == null) {
            CoreFoundation.getInstance().messages().sendRaw(sender,
                    CoreFoundation.getInstance().messages().getPrefix()
                    + " <red>No note with ID <white>#" + noteId + " <red>for " + target.getName() + ".");
            return true;
        }
        storage.remove(uuid, noteId);
        CoreFoundation.getInstance().messages().sendRaw(sender,
                CoreFoundation.getInstance().messages().getPrefix()
                + " <green>Removed note <white>#" + noteId + " <green>from " + target.getName() + ".");
        return true;
    }

    /** Permission gate: sub-node OR coreban.notes OR coreban.* (console always passes). */
    private boolean canManage(final Player p, final String node) {
        return p.hasPermission("coreban.*") || p.hasPermission("coreban.notes") || p.hasPermission(node);
    }

    /** A player is "findable" if they have joined before or are currently online. */
    private boolean found(final OfflinePlayer target) {
        return target != null && (target.hasPlayedBefore() || target.isOnline());
    }
}
