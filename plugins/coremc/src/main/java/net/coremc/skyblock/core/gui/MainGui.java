package net.coremc.skyblock.core.gui;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.storage.Island;
import net.coremc.skyblock.progression.ProgressionModule;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Main /is menu for players who already have an island.
 * Clean, spaced layout with all existing island features.
 */
public final class MainGui {

    private final JavaPlugin plugin;
    private final IslandApi api;

    public MainGui(final JavaPlugin plugin, final IslandApi api) {
        this.plugin = plugin;
        this.api = api;
    }

    public void open(final @NotNull Player player) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final Island island = api.getIsland(player.getUniqueId());
        if (island == null) {
            cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>You have no island.");
            return;
        }

        final InventoryGui gui = new InventoryGui(CoreMC.getInstance(), 5,
                "<bold><white>Island Menu", Material.BLACK_STAINED_GLASS_PANE);

        // Row 1 — navigation / core
        gui.setItem(10, ItemUtil.create(Material.COMPASS,
                "<bold><yellow>Go to Island",
                List.of("<gray>Teleport to your island spawn.", "<aqua>Click to teleport.")),
                e -> { e.setCancelled(true); api.teleportToIsland(player); });

        gui.setItem(12, ItemUtil.create(Material.NETHER_STAR,
                "<bold><gold>Island Upgrades",
                List.of("<gray>Spend Sky Tokens on permanent", "<gray>upgrades for your island.", "<aqua>Click to open.")),
                e -> { e.setCancelled(true); openUpgrades(player); });

        gui.setItem(14, ItemUtil.create(Material.GOLDEN_APPLE,
                "<bold><light_purple>Island Buffs",
                List.of("<gray>View active island-wide buffs", "<gray>and their multipliers.", "<aqua>Click to open.")),
                e -> { e.setCancelled(true); openBuffs(player); });

        gui.setItem(16, ItemUtil.create(Material.PLAYER_HEAD,
                "<bold><aqua>Island Members",
                List.of("<gray>Manage co-owners and members.", "<aqua>Click to view.")),
                e -> { e.setCancelled(true); openMembers(player, island); });

        // Row 2 — management
        gui.setItem(19, ItemUtil.create(Material.OAK_SIGN,
                "<bold><yellow>Island Settings",
                List.of("<gray>Biome, level, XP, members", "<gray>and island options.", "<aqua>Click to view.")),
                e -> { e.setCancelled(true); new SettingsGui(CoreMC.getInstance(), CoreMC.getInstance().islands().api(),
                        CoreMC.getInstance().islands().api().getIsland(player.getUniqueId())).open(player); });

        gui.setItem(21, ItemUtil.create(Material.FEATHER,
                "<bold><white>Island Top",
                List.of("<gray>See the top islands", "<gray>by level and stats.", "<aqua>Click to open.")),
                e -> { e.setCancelled(true); new TopGui(CoreMC.getInstance(), CoreMC.getInstance().islands().storage(),
                        CoreMC.getInstance().islands().api(), 1).open(player); });

        gui.setItem(23, ItemUtil.create(Material.ENCHANTED_BOOK,
                "<bold><green>Roles",
                List.of("<gray>Pick your progression role", "<gray>and spend role XP.", "<aqua>Click to open.")),
                e -> { e.setCancelled(true); CoreMC.getInstance().getCommand("role").execute(player, "role", new String[0]); });

        gui.setItem(25, ItemUtil.create(Material.NAME_TAG,
                "<bold><light_purple>Tags",
                List.of("<gray>Collect and equip chat tags.", "<aqua>Click to open.")),
                e -> { e.setCancelled(true);
                    if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("core-tags")) {
                        CoreMC.getInstance().getServer().dispatchCommand(player, "tags");
                    } else {
                        cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>Tags are unavailable.");
                    }
                });

        // Row 3 — info panel
        gui.setItem(30, ItemUtil.create(Material.PAPER,
                "<bold><white>Island Info",
                List.of(
                        "<gray>Level: <white>" + island.getLevel(),
                        "<gray>Biome: <white>" + island.getBiome().name().toLowerCase().replace("_", " "),
                        "<gray>Members: <white>" + island.getMemberCount())),
                null);

        gui.setItem(32, ItemUtil.create(Material.DIAMOND,
                "<bold><aqua>Sky Tokens",
                List.of("<gray>Balance: <aqua>"
                        + String.format(java.util.Locale.ROOT, "%,d",
                        CoreMC.getInstance().progression().tokens().get(player.getUniqueId())),
                        "<gray>Use /skytokens to view.")),
                e -> { e.setCancelled(true); CoreMC.getInstance().getServer().dispatchCommand(player, "skytokens"); });

        // Row 4 — danger zone
        gui.setItem(38, ItemUtil.create(Material.BARRIER,
                "<bold><red>Reset Island",
                List.of("<red>Warning: deletes your island!", "<gray>Requires the reset permission.")),
                e -> { e.setCancelled(true); resetIsland(e.getWhoClicked().getUniqueId()); });

        gui.setItem(40, ItemUtil.create(Material.CLOCK,
                "<bold><gray>Set Home",
                List.of("<gray>Set your island home", "<gray>to where you stand.")),
                e -> { e.setCancelled(true);
                    CoreMC.getInstance().islands().islandManager().setHome(island, player.getUniqueId(), player.getLocation());
                    cf.messages().sendRaw(player, cf.messages().getPrefix() + " <green>Island home set.");
                });

        gui.setItem(42, ItemUtil.create(Material.BARRIER,
                "<bold><gray>Close", List.of("<gray>Close this menu.")),
                e -> { e.setCancelled(true); player.closeInventory(); });

        gui.open(player);
    }

    private void openUpgrades(final Player player) {
        final ProgressionModule prog = CoreMC.getInstance().progression();
        if (prog != null) {
            prog.getUpgradesExecutor().onCommand(player, null, "upgrades", new String[0]);
        }
    }

    private void openBuffs(final Player player) {
        final ProgressionModule prog = CoreMC.getInstance().progression();
        if (prog != null) {
            prog.getBuffsExecutor().onCommand(player, null, "buffs", new String[0]);
        }
    }

    private void openMembers(final Player player, final Island island) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final List<String> lines = new ArrayList<>();
        lines.add("<bold><white>Island Members");
        lines.add("<gray>Owner: <white>" + org.bukkit.Bukkit.getOfflinePlayer(island.getOwner()).getName());
        int i = 1;
        for (final java.util.UUID m : island.getMembers()) {
            lines.add("<gray>Member " + i + ": <white>" + org.bukkit.Bukkit.getOfflinePlayer(m).getName());
            i++;
        }
        final InventoryGui gui = new InventoryGui(CoreMC.getInstance(), 4,
                "<bold><white>Island Members", Material.BLACK_STAINED_GLASS_PANE);
        int slot = 10;
        for (final String line : lines) {
            gui.setItem(slot++, ItemUtil.create(Material.PAPER, line, null), null);
        }
        gui.setItem(35, ItemUtil.create(Material.BARRIER, "<bold><gray>Close", null), e -> player.closeInventory());
        gui.open(player);
    }

    private void resetIsland(final java.util.UUID playerId) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final Player p = org.bukkit.Bukkit.getPlayer(playerId);
        if (p != null) {
            cf.messages().sendRaw(p, cf.messages().getPrefix() + " <red>Use /is reset to reset your island.");
        }
    }
}
