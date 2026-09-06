package net.coremc.skyblock.progression.command;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.FormatUtil;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.skyblock.core.gui.MainGui;
import net.coremc.skyblock.core.storage.Island;
import net.coremc.skyblock.progression.ProgressionModule;
import net.coremc.skyblock.progression.tree.TreeManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * /is upgrades - 27-slot menu with the six upgrade trees. Clicking a tree opens
 * its node list (purchased with Sky Tokens).
 */
public final class UpgradesCommand implements org.bukkit.command.CommandExecutor,
        org.bukkit.command.TabCompleter {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final ProgressionModule prog;

    public UpgradesCommand(final org.bukkit.plugin.java.JavaPlugin plugin, final ProgressionModule prog) {
        this.plugin = plugin;
        this.prog = prog;
    }

    @Override
    public boolean onCommand(final org.bukkit.command.CommandSender sender,
                             final org.bukkit.command.Command cmd, final String label,
                             final String[] args) {
        if (!(sender instanceof final Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        final CoreFoundation cf = CoreFoundation.getInstance();
        final Island island = CoreMC.getInstance().islands().api().getIsland(player.getUniqueId());
        if (island == null) {
            cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>You need an island first.");
            return true;
        }
        openMain(player, island);
        return true;
    }

    private void openMain(final Player player, final Island island) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final InventoryGui gui = new InventoryGui(plugin, 4,
                "<bold><white>Island Upgrades", Material.BLACK_STAINED_GLASS_PANE);

        final TreeManager tm = prog.trees();
        final List<TreeManager.TreeDef> list = tm.trees();
        final int[] slots = {10, 12, 14, 16, 28, 30, 32, 34};
        int i = 0;
        for (final TreeManager.TreeDef tree : list) {
            if (i >= slots.length) {
                break;
            }
            final int owned = countOwned(island.getId(), tree);
            final Material mat = categoryMaterial(tree.id());
            final String colour = categoryColour(tree.id());
            final List<String> lore = new ArrayList<>();
            lore.add("<gray>" + categoryDescription(tree.id()));
            lore.add("");
            lore.add("<gray>Nodes unlocked: <white>" + owned + "<gray>/<white>" + tree.nodes().size());
            lore.add("<aqua>Click to open");
            final TreeManager.TreeDef t = tree;
            gui.setItem(slots[i], ItemUtil.create(mat, "<bold>" + colour + tree.name() + " <gray>Upgrades", lore),
                    ev -> openTree(player, island, t));
            i++;
        }
        gui.setItem(31, ItemUtil.create(Material.ARROW, "<bold><gray>Back", null), ev -> {
            final Island is = CoreMC.getInstance().islands().api().getIsland(player.getUniqueId());
            if (is != null) new MainGui(plugin, CoreMC.getInstance().islands().api()).open(player);
            else player.closeInventory();
        });
        gui.open(player);
    }

    /** Representative item + colour + description for each upgrade category. */
    private static Material categoryMaterial(final String id) {
        return switch (id.toLowerCase(java.util.Locale.ROOT)) {
            case "mining" -> Material.IRON_PICKAXE;
            case "fishing" -> Material.FISHING_ROD;
            case "farming" -> Material.GOLDEN_HOE;
            case "slaying" -> Material.DIAMOND_SWORD;
            case "logging" -> Material.IRON_AXE;
            case "island" -> Material.NETHERITE_BLOCK;
            default -> Material.DIAMOND_BLOCK;
        };
    }

    private static String categoryColour(final String id) {
        return switch (id.toLowerCase(java.util.Locale.ROOT)) {
            case "mining" -> "<yellow>";
            case "fishing" -> "<aqua>";
            case "farming" -> "<green>";
            case "slaying" -> "<red>";
            case "logging" -> "<gold>";
            case "island" -> "<light_purple>";
            default -> "<white>";
        };
    }

    private static String categoryDescription(final String id) {
        return switch (id.toLowerCase(java.util.Locale.ROOT)) {
            case "mining" -> "Better mining speed, fortune and ore finds.";
            case "fishing" -> "Bigger catches, rare loot and token boosts.";
            case "farming" -> "Faster farms, crop yields and role XP.";
            case "slaying" -> "Combat power, mob drops and slayer perks.";
            case "logging" -> "Wood yields, tree perks and timber fortune.";
            case "island" -> "Island-wide upgrades: size, members and more.";
            default -> "Progression upgrades.";
        };
    }

    private void openTree(final Player player, final Island island, final TreeManager.TreeDef tree) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final String scopeKey = tree.scope() == TreeManager.Scope.ISLAND
                ? String.valueOf(island.getId()) : player.getUniqueId().toString();
        final TreeManager tm = prog.trees();
        final InventoryGui gui = new InventoryGui(plugin, 6,
                "<white>" + tree.name() + " Upgrades", Material.BLACK_STAINED_GLASS_PANE);
        int slot = 0;
        for (final TreeManager.NodeDef node : tree.nodes().values()) {
            final int level = tm.getLevel(scopeKey, tree.id(), node.id());
            final int max = tm.maxLevel(tree.id(), node.id());
            final boolean maxed = tm.isMaxed(scopeKey, tree.id(), node.id());
            final boolean reqMet = tm.requirementsMet(scopeKey, tree.id(), node.id());
            final List<String> lore = new ArrayList<>();
            lore.add("<gray>" + node.description());
            lore.add("<gray>Level: <white>" + level + "/" + max);
            if (!reqMet) {
                lore.add("<red>Requires: " + String.join(", ", tm.unmetRequirements(scopeKey, tree.id(), node.id())));
            } else if (maxed) {
                lore.add("<green>MAXED");
            } else {
                lore.add("<gray>Cost: <yellow>" + FormatUtil.formatTokens(tm.nextCost(tree.id(), node.id())) + " tokens");
                lore.add("<aqua>Click to purchase");
            }
            final Material mat = maxed ? Material.LIME_DYE
                    : (reqMet ? Material.EMERALD : Material.GRAY_DYE);
            final TreeManager.NodeDef n = node;
            gui.setItem(slot++, ItemUtil.create(mat, "<white>" + node.name(), lore), ev -> {
                if (!reqMet || maxed) {
                    return;
                }
                final long cost = tm.nextCost(tree.id(), n.id());
                final long removed = prog.tokens().remove(player.getUniqueId(), cost, "upgrade:" + tree.id() + "/" + n.id(), player.getName());
                if (removed < 0) {
                    cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>Not enough Sky Tokens.");
                    return;
                }
                tm.purchase(scopeKey, tree.id(), n.id());
                cf.messages().sendRaw(player, cf.messages().getPrefix()
                        + " <green>Purchased " + n.name() + " <gray>(level " + tm.getLevel(scopeKey, tree.id(), n.id()) + ").");
                if (tree.scope() == TreeManager.Scope.ISLAND) {
                    net.coremc.skyblock.core.listener.IslandBorderListener.refreshIslandBorders(island.getId());
                }
                openTree(player, island, tree);
            });
        }
        addBack(gui, player, island);
        gui.open(player);
    }

    private int countOwned(final int islandId, final TreeManager.TreeDef tree) {
        final String key = tree.scope() == TreeManager.Scope.ISLAND
                ? String.valueOf(islandId) : "";
        int c = 0;
        for (final TreeManager.NodeDef n : tree.nodes().values()) {
            if (prog.trees().getLevel(key, tree.id(), n.id()) >= 1) {
                c++;
            }
        }
        return c;
    }

    private void addBack(final InventoryGui gui, final Player player, final Island island) {
        gui.setItem(49, ItemUtil.create(Material.ARROW, "<gray>Back", null), ev -> openMain(player, island));
    }

    @Override
    public List<String> onTabComplete(final org.bukkit.command.CommandSender sender,
                                       final org.bukkit.command.Command cmd, final String label,
                                       final String[] args) {
        return new ArrayList<>();
    }
}
