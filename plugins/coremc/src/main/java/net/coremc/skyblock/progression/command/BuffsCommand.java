package net.coremc.skyblock.progression.command;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.storage.Island;
import net.coremc.skyblock.progression.ProgressionModule;
import net.coremc.skyblock.progression.buff.BuffManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /is buffs - island-wide buffs earned through island progression (no role required).
 */
public final class BuffsCommand implements org.bukkit.command.CommandExecutor {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final ProgressionModule prog;

    public BuffsCommand(final org.bukkit.plugin.java.JavaPlugin plugin, final ProgressionModule prog) {
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
        open(player, island);
        return true;
    }

    public void open(final Player player, final Island island) {
        final InventoryGui gui = new InventoryGui(plugin, 6,
                "<white>Island Buffs", Material.BLACK_STAINED_GLASS_PANE);
        final List<BuffManager.BuffRow> rows = prog.buffs().rows(island.getId());
        if (rows.isEmpty()) {
            gui.setItem(13, ItemUtil.create(Material.BARRIER,
                    "<gray>No buffs yet", List.of("<gray>Unlock Island upgrades (/is upgrades)")), ev -> {});
        } else {
            int slot = 0;
            for (final BuffManager.BuffRow row : rows) {
                final List<String> lore = new ArrayList<>();
                lore.add("<gray>" + row.label());
                lore.add("<gray>Level: <white>" + row.level());
                lore.add("<aqua>" + row.detail());
                gui.setItem(slot++, ItemUtil.create(Material.GOLDEN_APPLE,
                        "<yellow>" + row.name(), lore), ev -> {});
            }
        }
        gui.open(player);
    }
}
