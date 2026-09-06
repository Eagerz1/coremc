package net.coremc.skyblock.playerdata;

import net.coremc.foundation.CoreFoundation;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Administrator commands for the persistent player-data system.
 *
 * <p>Permissions:</p>
 * <ul>
 *   <li>{@code core.playerdata} — full access (info on anyone, save, reload, backup).</li>
 *   <li>No permission — a player may only view their OWN summary via
 *       {@code /playerdata} (balances + owned cosmetics), never other players'
 *       data and never save/reload/backup.</li>
 * </ul>
 *
 * <p>Sensitive internals (file paths, data_version mechanics, backup layout) are
 * never exposed to normal players.</p>
 */
public final class PlayerDataCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final PlayerDataManager manager;

    public PlayerDataCommand(final JavaPlugin plugin, final PlayerDataManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final boolean admin = sender.hasPermission(PlayerDataModule.ADMIN_PERM) || sender.isOp();

        // /playerdata  -> own summary (any player) or target info (admin)
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                cf.messages().sendRaw(sender, cf.messages().getPrefix()
                        + " <red>Specify a player: /playerdata <player>");
                return true;
            }
            showSummary(sender, p.getUniqueId(), p.getName());
            return true;
        }

        final String first = args[0].toLowerCase(Locale.ROOT);

        switch (first) {
            case "backup" -> {
                if (!admin) { noPerm(cf, sender); return true; }
                final int n = manager.backupNow();
                cf.messages().sendRaw(sender, cf.messages().getPrefix()
                        + " <green>Backed up <aqua>" + n + "<green> player files.");
                return true;
            }
            case "save", "reload" -> {
                if (!admin) { noPerm(cf, sender); return true; }
                if (args.length < 2) {
                    cf.messages().sendRaw(sender, cf.messages().getPrefix()
                            + " <red>Usage: /playerdata " + first + " <player>");
                    return true;
                }
                final OfflinePlayer target = resolve(args[1]);
                if (target == null) {
                    cf.messages().sendRaw(sender, cf.messages().getPrefix()
                            + " <red>Player not found: " + args[1]);
                    return true;
                }
                if (first.equals("save")) {
                    manager.save(target.getUniqueId());
                    cf.messages().sendRaw(sender, cf.messages().getPrefix()
                            + " <green>Saved data for <aqua>" + target.getName());
                } else {
                    // reload = drop from cache so next access re-reads disk
                    manager.unload(target.getUniqueId());
                    manager.get(target.getUniqueId());
                    cf.messages().sendRaw(sender, cf.messages().getPrefix()
                            + " <green>Reloaded data for <aqua>" + target.getName());
                }
                return true;
            }
            default -> {
                // /playerdata <player>  -> info
                if (!admin) {
                    // Non-admins may only inspect themselves.
                    if (sender instanceof Player p && p.getName().equalsIgnoreCase(args[0])) {
                        showSummary(sender, p.getUniqueId(), p.getName());
                    } else {
                        noPerm(cf, sender);
                    }
                    return true;
                }
                final OfflinePlayer target = resolve(args[0]);
                if (target == null) {
                    cf.messages().sendRaw(sender, cf.messages().getPrefix()
                            + " <red>Player not found: " + args[0]);
                    return true;
                }
                showFull(sender, target.getUniqueId(), target.getName());
                return true;
            }
        }
    }

    private void showSummary(final CommandSender sender, final UUID uuid, final String name) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final PlayerData d = manager.get(uuid);
        cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <white>Your CoreMC data:");
        cf.messages().sendRaw(sender, "  <gray>• <white>Credits: <aqua>" + String.format(Locale.ROOT, "%,d", d.credits()));
        cf.messages().sendRaw(sender, "  <gray>• <white>SkyTokens: <aqua>" + String.format(Locale.ROOT, "%,d", d.skyTokens()));
        cf.messages().sendRaw(sender, "  <gray>• <white>Money: <aqua>" + String.format(Locale.ROOT, "%,.2f", d.money()));
        cf.messages().sendRaw(sender, "  <gray>• <white>Cosmetics: <aqua>" + totalCosmetics(d)
                + " <gray>(skins " + d.skins().size() + ", tags " + d.tags().size()
                + ", gradients " + d.gradients().size() + ", sets " + d.sets().size()
                + ", companions " + d.companions().size() + ")");
    }

    private void showFull(final CommandSender sender, final UUID uuid, final String name) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final PlayerData d = manager.get(uuid);
        cf.messages().sendRaw(sender, cf.messages().getPrefix() + " <white>Player Data: <aqua>" + name);
        cf.messages().sendRaw(sender, "  <gray>• <white>UUID: <aqua>" + uuid);
        cf.messages().sendRaw(sender, "  <gray>• <white>Credits: <aqua>" + String.format(Locale.ROOT, "%,d", d.credits()));
        cf.messages().sendRaw(sender, "  <gray>• <white>SkyTokens: <aqua>" + String.format(Locale.ROOT, "%,d", d.skyTokens()));
        cf.messages().sendRaw(sender, "  <gray>• <white>Money: <aqua>" + String.format(Locale.ROOT, "%,.2f", d.money()));
        cf.messages().sendRaw(sender, "  <gray>• <white>Skins: <aqua>" + d.skins());
        cf.messages().sendRaw(sender, "  <gray>• <white>Tags: <aqua>" + d.tags());
        cf.messages().sendRaw(sender, "  <gray>• <white>Gradients: <aqua>" + d.gradients());
        cf.messages().sendRaw(sender, "  <gray>• <white>Sets: <aqua>" + d.sets());
        cf.messages().sendRaw(sender, "  <gray>• <white>Companions: <aqua>" + d.companions());
        cf.messages().sendRaw(sender, "  <gray>• <white>Keys: <aqua>" + formatKeys(d.allKeys()));
        cf.messages().sendRaw(sender, "  <gray>• <white>First join: <aqua>" + d.firstJoin()
                + "  <white>Last seen: <aqua>" + d.lastSeen());
    }

    private static int totalCosmetics(final PlayerData d) {
        return d.skins().size() + d.tags().size() + d.gradients().size()
                + d.sets().size() + d.companions().size();
    }

    private static String formatKeys(final java.util.Map<String, Integer> keys) {
        if (keys.isEmpty()) return "none";
        final List<String> parts = new ArrayList<>();
        for (final var e : keys.entrySet()) {
            if (e.getValue() > 0) parts.add(e.getKey() + "=" + e.getValue());
        }
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    private OfflinePlayer resolve(final String nameOrUuid) {
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(nameOrUuid));
        } catch (final IllegalArgumentException ignored) {
            return Bukkit.getOfflinePlayer(nameOrUuid);
        }
    }

    private void noPerm(final CoreFoundation cf, final CommandSender sender) {
        cf.messages().send(sender, "messages.no-permission");
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final List<String> out = new ArrayList<>();
        if (!(sender.hasPermission(PlayerDataModule.ADMIN_PERM) || sender.isOp())) {
            return out; // non-admins get no tab hints for other players
        }
        if (args.length == 1) {
            out.addAll(List.of("backup", "save", "reload"));
            for (final Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("save") || args[0].equalsIgnoreCase("reload"))) {
            for (final Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        return out;
    }
}
