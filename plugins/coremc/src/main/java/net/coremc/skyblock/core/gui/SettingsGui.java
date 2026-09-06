package net.coremc.skyblock.core.gui;
import org.bukkit.plugin.java.JavaPlugin;

import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.FormatUtil;
import net.coremc.foundation.util.ItemUtil;

import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.storage.Island;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Island settings overview GUI. Shows level, XP, members, biome and links out
 * to the progression modules when installed.
 */
public final class SettingsGui {

    private final JavaPlugin plugin;
    private final IslandApi api;
    private final Island island;

    public SettingsGui(final JavaPlugin plugin, final IslandApi api, final Island island) {
        this.plugin = plugin;
        this.api = api;
        this.island = island;
    }

    public void open(final @NotNull Player player) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final InventoryGui gui = new InventoryGui(plugin, 3,
                "<white>Island Settings", Material.BLACK_STAINED_GLASS_PANE);

        gui.setItem(10, ItemUtil.create(Material.GRASS_BLOCK, "<yellow>Biome",
                List.of("<gray>" + island.getBiome().name().toLowerCase().replace("_", " "))), null);
        gui.setItem(12, ItemUtil.create(Material.EXPERIENCE_BOTTLE, "<yellow>Level",
                List.of("<gray>" + FormatUtil.formatNumber(island.getLevel()))), null);
        gui.setItem(14, ItemUtil.create(Material.EMERALD, "<yellow>XP",
                List.of("<gray>" + FormatUtil.formatNumber(island.getXp()))), null);
        gui.setItem(16, ItemUtil.create(Material.PLAYER_HEAD, "<yellow>Members",
                List.of("<gray>" + island.getMemberCount())), null);

        gui.open(player);
    }
}
