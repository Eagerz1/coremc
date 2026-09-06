package net.coremc.skyblock.events;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.util.ColorUtil;
import net.coremc.foundation.util.PermissionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Live-events commands.
 *
 * <p>Players: {@code /events} shows the current event, time left, next event and the
 * active multiplier. Admins (permission {@code coremc.events.admin}) get the management
 * verbs: {@code /event current|next|start <id>|stop|reload}. {@code /event} with no
 * args is an alias of {@code /events} for everyone.</p>
 */
public final class EventsCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = PermissionUtil.perm("events.admin");

    private final EventManager mgr;

    public EventsCommand(final EventManager mgr) {
        this.mgr = mgr;
    }

    @Override
    public boolean onCommand(final @NotNull CommandSender sender, final @NotNull Command cmd,
                              final @NotNull String label, final @NotNull String[] args) {
        final String name = cmd.getName().toLowerCase(java.util.Locale.ROOT);
        final boolean isAdminVerb = name.equals("event");

        // /event with no subcommand -> behave like /events (info for everyone).
        if (args.length == 0) {
            return info(sender);
        }

        final String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "current":
            case "next":
                if (isAdminVerb && !perm(sender)) return true;
                return info(sender);
            case "start":
                if (!perm(sender)) return true;
                return start(sender, args);
            case "stop":
                if (!perm(sender)) return true;
                return stop(sender);
            case "reload":
                if (!perm(sender)) return true;
                return reload(sender);
            default:
                // Unknown subcommand -> just show info (players) or usage (admins).
                if (isAdminVerb && !perm(sender)) return true;
                return info(sender);
        }
    }

    private boolean info(final CommandSender sender) {
        final var line = "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>";
        if (!mgr.isEnabled()) {
            sender.sendMessage(ColorUtil.parse(line + "\n<red>Live events are disabled.</red>\n" + line));
            return true;
        }
        final var active = mgr.activeDefinition();
        final Component body;
        if (active == null) {
            body = ColorUtil.parse(line + "\n"
                    + "<bold><yellow>NO ACTIVE EVENT</yellow></bold>\n\n"
                    + "<gray>Next:</gray> <white>" + safeName(mgr.nextDefinition()) + "</white>\n"
                    + "<gray>Starts in:</gray> <white>" + EventManager.formatTime(mgr.nextInSeconds()) + "</white>\n"
                    + line);
        } else {
            final String mult = String.format(java.util.Locale.ROOT, "x%.1f", active.multiplier());
            body = ColorUtil.parse(line + "\n"
                    + "<bold><" + active.sectionColour(1) + ">" + active.displayName().toUpperCase() + "</" + active.sectionColour(1) + "></bold>\n\n"
                    + "<gray>Time left:</gray> <white>" + EventManager.formatTime(mgr.remainingSeconds()) + "</white>\n"
                    + "<gray>Multiplier:</gray> <green>" + mult + "</green>\n"
                    + "<gray>Next:</gray> <white>" + safeName(mgr.nextDefinition()) + "</white>\n"
                    + line);
        }
        sender.sendMessage(body);
        return true;
    }

    private boolean start(final CommandSender sender, final String[] args) {
        final String id = args.length >= 2 ? args[1] : null;
        if (id != null && mgr.allDefinitions().stream().noneMatch(d -> d.id().equalsIgnoreCase(id))) {
            sender.sendMessage(ColorUtil.parse("<red>Unknown event: " + id
                    + "</red>\n<gray>Valid:</gray> " + String.join(", ", mgr.allDefinitions().stream()
                    .map(d -> d.id()).toList())));
            return true;
        }
        final boolean ok = mgr.forceStart(id);
        sender.sendMessage(ColorUtil.parse(ok
                ? "<green>Event " + (id == null ? "started (next in rotation)" : id) + ".</green>"
                : "<red>Could not start event (events disabled or none configured).</red>"));
        return true;
    }

    private boolean stop(final CommandSender sender) {
        if (!mgr.isActive()) {
            sender.sendMessage(ColorUtil.parse("<gray>No event is currently active.</gray>"));
            return true;
        }
        mgr.forceStop();
        sender.sendMessage(ColorUtil.parse("<green>Active event stopped; downtime resumed.</green>"));
        return true;
    }

    private boolean reload(final CommandSender sender) {
        mgr.reloadConfig();
        sender.sendMessage(ColorUtil.parse("<green>Events config reloaded.</green>"));
        return true;
    }

    private boolean perm(final CommandSender sender) {
        if (sender instanceof final Player p && !PermissionUtil.has(p, ADMIN)) {
            sender.sendMessage(ColorUtil.parse("<red>You do not have permission to manage events.</red>"));
            return false;
        }
        return true;
    }

    private String safeName(final EventDefinition d) {
        return d == null ? "—" : d.displayName();
    }

    @Override
    public @NotNull List<String> onTabComplete(final @NotNull CommandSender sender, final @NotNull Command cmd,
                                               final @NotNull String label, final @NotNull String[] args) {
        if (!(sender instanceof final Player p) || !PermissionUtil.has(p, ADMIN)) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("current", "next", "start", "stop", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            final String prefix = args[1].toLowerCase(java.util.Locale.ROOT);
            return mgr.allDefinitions().stream().map(EventDefinition::id)
                    .filter(id -> id.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
