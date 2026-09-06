package net.coremc.skyblock.core.command;
import net.coremc.coremc.CoreMC;
import org.bukkit.plugin.java.JavaPlugin;

import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.ColorUtil;
import net.coremc.foundation.util.FormatUtil;
import net.coremc.foundation.util.ItemUtil;

import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.island.IslandManager;
import net.coremc.skyblock.core.storage.Island;
import net.coremc.skyblock.core.gui.MainGui;
import net.coremc.skyblock.core.gui.TopGui;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * /is command tree.
 */
public final class IslandCommand implements org.bukkit.command.CommandExecutor,
        org.bukkit.command.TabCompleter {

    private final JavaPlugin plugin;
    private final IslandApi api;
    private final net.coremc.skyblock.core.island.IslandManager manager;

    public IslandCommand(final JavaPlugin plugin, final IslandApi api,
                         final net.coremc.skyblock.core.island.IslandManager manager) {
        this.plugin = plugin;
        this.api = api;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(final @NotNull CommandSender sender, final @NotNull Command cmd,
                             final @NotNull String label, final @NotNull String[] args) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        if (!(sender instanceof final Player player)) {
            // Console-only debug commands
            if (args.length > 0 && args[0].equalsIgnoreCase("debugcreate")) {
                if (args.length < 3) {
                    sender.sendMessage("Usage: /is debugcreate <player> <plains|desert|mushroom>");
                    return true;
                }
                final org.bukkit.OfflinePlayer tp = Bukkit.getOfflinePlayer(args[1]);
                final Island.Biome b = Island.Biome.valueOf(args[2].toUpperCase(java.util.Locale.ROOT));
                final Location spawn = manager.nextIslandSpawn();
                final Island created = api.createIsland(tp.getUniqueId(), b, spawn);
                manager.buildPlatform(created);
                sender.sendMessage("Created island " + created.getId() + " at " + spawn);
                return true;
            }
            sender.sendMessage("Players only.");
            return true;
        }
        final Island island = api.getIsland(player.getUniqueId());

        if (args.length == 0) {
            if (island == null) {
                createIsland(player);
            } else {
                new MainGui(plugin, api).open(player);
            }
            return true;
        }

        final String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (island != null) {
                    cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>You already have an island.");
                    return true;
                }
                createIsland(player);
            }
            case "go", "home" -> {
                if (island == null) {
                    noIsland(cf, player);
                    return true;
                }
                api.teleportToIsland(player);
                cf.messages().sendRaw(player, cf.messages().getPrefix() + " <green>Teleported to your island.");
            }
            case "sethome" -> {
                if (island == null) { noIsland(cf, player); return true; }
                manager.setHome(island, player.getUniqueId(), player.getLocation());
                cf.messages().sendRaw(player, cf.messages().getPrefix() + " <green>Island home set.");
            }
            case "members" -> {
                if (island == null) { noIsland(cf, player); return true; }
                showMembers(cf, player, island);
            }
            case "invite" -> {
                if (island == null) { noIsland(cf, player); return true; }
                if (args.length < 2) {
                    cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>Usage: /is invite <player>");
                    return true;
                }
                invite(player, island, args[1]);
            }
            case "kick" -> {
                if (island == null) { noIsland(cf, player); return true; }
                if (args.length < 2) {
                    cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>Usage: /is kick <player>");
                    return true;
                }
                kick(player, island, args[1]);
            }
            case "leave" -> {
                if (island == null) { noIsland(cf, player); return true; }
                leave(player, island);
            }
            case "settings" -> {
                if (island == null) { noIsland(cf, player); return true; }
                new net.coremc.skyblock.core.gui.SettingsGui(plugin, api, island).open(player);
            }
            case "upgrades" -> {
                final net.coremc.skyblock.progression.ProgressionModule prog = CoreMC.getInstance().progression();
                if (prog != null) {
                    prog.getUpgradesExecutor().onCommand(player, null, "upgrades", new String[0]);
                } else {
                    cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>Upgrades module not installed.");
                }
            }
            case "buffs" -> {
                final net.coremc.skyblock.progression.ProgressionModule prog = CoreMC.getInstance().progression();
                if (prog != null) {
                    prog.getBuffsExecutor().onCommand(player, null, "buffs", new String[0]);
                } else {
                    cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>Buffs module not installed.");
                }
            }
            case "top" -> new TopGui(plugin, CoreMC.getInstance().islands().storage(), api, 1).open(player);
            case "delete" -> {
                if (island == null) { noIsland(cf, player); return true; }
                if (!island.getOwner().equals(player.getUniqueId())) {
                    cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>Only the island owner can delete it.");
                    return true;
                }
                // Two-step confirmation to avoid accidental wipes.
                if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
                    deleteIslandData(island, player);
                } else {
                    cf.messages().sendRaw(player, cf.messages().getPrefix()
                            + " <red>WARNING: this permanently deletes your island, all generators, spawners, mobs and progression.");
                    cf.messages().sendRaw(player, cf.messages().getPrefix()
                            + " <yellow>Type <bold>/is delete confirm <gray>to proceed.");
                }
            }
            case "reset" -> {
                if (island == null) { noIsland(cf, player); return true; }
                if (!island.getOwner().equals(player.getUniqueId())) {
                    cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>Only the island owner can reset it.");
                    return true;
                }
                deleteIslandData(island, player);
            }
            default -> help(cf, player);
        }
        return true;
    }

    /**
     * Fully wipe an island: removes it from storage, clears every block in the
     * island region, removes all placed generators/spawners (and their mobs),
     * resets progression (upgrade trees + island XP + spawner kill progress),
     * clears saved homes, and boots the player to the overworld. Without this,
     * a "delete" only touched the database and the island (blocks, gens, mobs)
     * stayed in the world — which is why reset appeared to do nothing.
     */
    private void deleteIslandData(final Island island, final Player player) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final int id = island.getId();

        // 1. Wipe placed generators + spawners (and their stacked mobs) for this island.
        CoreMC.getInstance().gens().generators().clearIsland(id);
        CoreMC.getInstance().spawners().spawners().clearIsland(id);

        // 2. Clear progression: upgrade trees + island XP.
        final net.coremc.skyblock.progression.ProgressionModule prog = CoreMC.getInstance().progression();
        if (prog != null) {
            prog.trees().clearIsland(id);
        }

        // 3. Clear the physical island region in the world.
        final Location c = island.getSpawn();
        if (c != null && c.getWorld() != null) {
            final double radius = manager.getIslandRadius(island);
            final int r = (int) Math.ceil(radius) + 1;
            final int cx = c.getBlockX();
            final int cz = c.getBlockZ();
            final int baseY = c.getBlockY();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int y = baseY - 5; y <= baseY + 30; y++) {
                        c.getWorld().getBlockAt(cx + dx, y, cz + dz).setType(org.bukkit.Material.AIR);
                    }
                }
            }
        }

        // 4. Clear saved homes for this island.
        final java.util.Set<String> homeKeys = plugin.getConfig()
                .getConfigurationSection("island.homes." + id) == null ? java.util.Collections.emptySet()
                : plugin.getConfig().getConfigurationSection("island.homes." + id).getKeys(false);
        for (final String k : homeKeys) {
            plugin.getConfig().set("island.homes." + id + "." + k, null);
        }
        plugin.saveConfig();

        // 5. Remove from storage (islands + members + stats).
        CoreMC.getInstance().islands().storage().deleteIsland(id);

        // 6. Boot the player to the main world so they aren't standing in the void.
        final org.bukkit.World overworld = org.bukkit.Bukkit.getWorlds().get(0);
        if (player.getWorld().equals(c == null ? null : c.getWorld()) && c != null
                && c.getWorld() != null && c.getWorld() != overworld) {
            player.teleport(new Location(overworld, 0.5, overworld.getSpawnLocation().getY(), 0.5));
        }

        cf.messages().sendRaw(player, cf.messages().getPrefix()
                + " <green>Island deleted. Create a new one with /is create.");
    }

    private void invite(final Player player, final Island island, final String name) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>You cannot invite yourself.");
            return;
        }
        api.addInvite(island, target.getUniqueId());
        cf.messages().sendRaw(player, cf.messages().getPrefix() + " <green>Invited " + name + ".");
    }

    private void kick(final Player player, final Island island, final String name) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        if (!island.getMembers().contains(target.getUniqueId())) {
            cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>That player is not a member.");
            return;
        }
        CoreMC.getInstance().islands().storage().removeMember(island.getId(), target.getUniqueId());
        cf.messages().sendRaw(player, cf.messages().getPrefix() + " <green>Kicked " + name + ".");
    }

    private void leave(final Player player, final Island island) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        if (island.getOwner().equals(player.getUniqueId())) {
            cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>The owner cannot leave. Transfer or reset.");
            return;
        }
        CoreMC.getInstance().islands().storage().removeMember(island.getId(), player.getUniqueId());
        cf.messages().sendRaw(player, cf.messages().getPrefix() + " <green>You left the island.");
    }

    private void showMembers(final CoreFoundation cf, final Player player, final Island island) {
        final List<String> lines = new ArrayList<>();
        lines.add("<gray>Owner: <white>" + Bukkit.getOfflinePlayer(island.getOwner()).getName());
        int i = 1;
        for (final UUID m : island.getMembers()) {
            lines.add("<gray>Member " + i + ": <white>" + Bukkit.getOfflinePlayer(m).getName());
            i++;
        }
        for (final String l : lines) {
            player.sendMessage(ColorUtil.parse(l, cf.messages().getPrefix()));
        }
    }

    private void noIsland(final CoreFoundation cf, final Player player) {
        cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>You have no island. Use /is create.");
    }

    /** Create the player's island immediately with the old-fashioned SkyBlock
     *  schematic and teleport them straight to it. No GUI. */
    private void createIsland(final Player player) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final IslandManager manager = CoreMC.getInstance().islands().islandManager();
        final Location spawn = manager.nextIslandSpawn();
        final Island created = api.createIsland(player.getUniqueId(),
                Island.Biome.valueOf(plugin.getConfig().getString("island.default-biome", "PLAINS")
                        .toUpperCase(java.util.Locale.ROOT)), spawn);
        if (created == null) {
            cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>Failed to create island. Check console.");
            return;
        }
        manager.buildPlatform(created);
        manager.sendToIsland(player, created);
        cf.messages().sendRaw(player, cf.messages().getPrefix() + " <green>Island created. Welcome home!");
    }

    private void help(final CoreFoundation cf, final Player player) {
        final String[] cmds = {"create", "go", "home", "sethome", "members", "invite <p>", "kick <p>",
                "leave", "settings", "upgrades", "buffs", "top", "reset", "delete"};
        for (final String c : cmds) {
            player.sendMessage(ColorUtil.parse("<gray>/is " + c, cf.messages().getPrefix()));
        }
    }

    @Override
    public @NotNull List<String> onTabComplete(final @NotNull CommandSender sender, final @NotNull Command cmd,
                                               final @NotNull String label, final @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "go", "home", "sethome", "members", "invite", "kick",
                    "leave", "settings", "upgrades", "buffs", "top", "reset", "delete");
        }
        return new ArrayList<>();
    }
}
