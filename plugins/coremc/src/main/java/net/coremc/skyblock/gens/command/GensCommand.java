package net.coremc.skyblock.gens.command;
import org.bukkit.plugin.java.JavaPlugin;
import net.coremc.coremc.CoreMC;

import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.Economy;
import net.coremc.foundation.util.FormatUtil;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.storage.Island;

import net.coremc.skyblock.gens.generator.GeneratorDef;
import net.coremc.skyblock.gens.generator.GeneratorManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * /gens - shop GUI to buy and place generators.
 *
 * Clean layout: one generator per slot, generous spacing (no cramped blocks),
 * BOLD titles, NO italics, normal Minecraft font. Shows item produced, interval,
 * price, current ownership and the island generator cap.
 */
public final class GensCommand implements org.bukkit.command.CommandExecutor,
        org.bukkit.command.TabCompleter {

    private final JavaPlugin plugin;
    private final GeneratorManager generators;
    private final Economy economy;

    public GensCommand(final JavaPlugin plugin, final GeneratorManager generators) {
        this.plugin = plugin;
        this.generators = generators;
        this.economy = new Economy(plugin);
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
        open(player, island);
        return true;
    }

    public void open(final Player player, final Island island) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final InventoryGui gui = new InventoryGui(plugin, 6,
                "<white>Generators", Material.BLACK_STAINED_GLASS_PANE);

        final int cap = generators.capFor(island.getId());
        final int owned = generators.totalFor(island.getId());

        // Header: island generator cap (slot 4).
        final List<String> headLore = new ArrayList<>();
        headLore.add("<gray>Current limit: <yellow>" + cap);
        headLore.add("<gray>Placed: <white>" + owned + " <gray>/ <white>" + cap);
        headLore.add("");
        headLore.add("<gray>Click a generator to buy & place it.");
        gui.setItem(4, ItemUtil.create(Material.ENDER_CHEST, "<bold>Generator Limit", headLore), null);

        // Generators laid out with spacing (every other column empty-ish via row layout).
        int slot = 8;
        for (final GeneratorDef def : generators.definitions()) {
            if (slot >= 54) {
                break;
            }
            final int ownedType = generators.countFor(island.getId(), def.id());
            final List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("<gray>Produces:");
            if (def.produce().size() == 1) {
                lore.add("<green>• <white>" + human(def.produce().get(0)));
            } else {
                for (final Material m : def.produce()) {
                    lore.add("<green>• <white>" + human(m));
                }
            }
            lore.add("");
            lore.add("<gray>Generation Time:");
            lore.add("<aqua>• <white>" + def.interval() + (def.interval() == 1 ? " second" : " seconds"));
            lore.add("");
            lore.add("<gray>Price:");
            lore.add("<yellow>• <white>$" + FormatUtil.formatNumber((long) def.price()));
            lore.add("");
            lore.add("<gray>Owned: <white>" + ownedType);
            lore.add("");
            if (owned >= cap) {
                lore.add("<red>Island limit reached (" + owned + "/" + cap + ")");
            } else {
                lore.add("<green>• Click to buy & place");
            }
            gui.setItem(slot, ItemUtil.create(def.material(), "<bold>" + def.name(), lore),
                    ev -> buy(player, island, def));
            // Advance, skipping the last column of each row for visual breathing room.
            slot += 2;
            if (slot == 16 || slot == 25 || slot == 34 || slot == 43) {
                slot += 2;
            }
        }
        gui.open(player);
    }

    private void buy(final Player player, final Island island, final GeneratorDef def) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final int cap = generators.capFor(island.getId());
        if (generators.totalFor(island.getId()) >= cap) {
            cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>Generator limit reached (" + cap + "). Upgrade it in /is upgrades.");
            return;
        }
        if (!economy.charge(player.getUniqueId(), def.price(), "gens:" + def.id())) {
            cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>Not enough funds for " + def.name() + ".");
            return;
        }
        generators.give(player, def.id());
        cf.messages().sendRaw(player, cf.messages().getPrefix() + " <green>Bought " + def.name() + ". Place it on your island!");
        // Refresh GUI so counts/cap update.
        final Island refreshed = CoreMC.getInstance().islands().api().getIsland(player.getUniqueId());
        if (refreshed != null) {
            open(player, refreshed);
        }
    }

    private static String prettyItem(final GeneratorDef def) {
        if (def.produce().size() == 1) {
            return human(def.produce().get(0)) + " x" + def.produceAmount();
        }
        return "Mixed (" + def.produce().size() + " crops)";
    }

    private static String human(final Material m) {
        final String n = m.name().toLowerCase().replace("_", " ");
        return n.charAt(0) + n.substring(1);
    }

    @Override
    public List<String> onTabComplete(final org.bukkit.command.CommandSender sender,
                                      final org.bukkit.command.Command cmd, final String label,
                                      final String[] args) {
        return new ArrayList<>();
    }
}
