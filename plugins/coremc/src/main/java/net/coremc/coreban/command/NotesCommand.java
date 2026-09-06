package net.coremc.coreban.command;

import org.bukkit.plugin.java.JavaPlugin;

import net.coremc.coreban.model.Note;
import net.coremc.coreban.storage.NoteStorage;
import net.coremc.foundation.CoreFoundation;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * /notes <player>
 */
public final class NotesCommand implements org.bukkit.command.CommandExecutor {

    private final JavaPlugin plugin;
    private final NoteStorage storage;

    public NotesCommand(final JavaPlugin plugin, final NoteStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (sender instanceof final Player p && !p.hasPermission("coreban.*")
                && !p.hasPermission("coreban.notes")) {
            CoreFoundation.getInstance().messages().send(sender, "messages.no-permission");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("/notes <player>");
            return true;
        }
        final OfflinePlayer target = CommandUtil.resolve(args[0]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            CoreFoundation.getInstance().messages().send(sender, "messages.player-not-found");
            return true;
        }
        final List<Note> notes = storage.notes(target.getUniqueId());
        final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        CoreFoundation.getInstance().messages().sendRaw(sender,
                CoreFoundation.getInstance().messages().getPrefix()
                + " <yellow>Notes for " + target.getName() + " (" + notes.size() + ")");
        if (notes.isEmpty()) {
            CoreFoundation.getInstance().messages().sendRaw(sender, "  <gray>No notes on record.");
            return true;
        }
        for (final Note n : notes) {
            CoreFoundation.getInstance().messages().sendRaw(sender,
                    "  <gray>#" + n.id() + " <white>by " + n.staff()
                    + " <gray>@ " + fmt.format(new Date(n.createdAt() * 1000))
                    + ": <white>" + n.content());
        }
        return true;
    }
}
