package net.coremc.skyblock.core.gui;
import org.bukkit.plugin.java.JavaPlugin;

import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.FormatUtil;
import net.coremc.foundation.util.ItemUtil;

import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.storage.Island;
import net.coremc.skyblock.core.storage.IslandStorage;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

/**
 * /is top GUI with three categories (Solo / Duo / Team) and Top 10 islands
 * plus the viewer's own position.
 */
public final class TopGui {

    private final JavaPlugin plugin;
    private final IslandStorage storage;
    private final IslandApi api;
    private final int category; // 1 solo, 2 duo, 3 team

    public TopGui(final JavaPlugin plugin, final IslandStorage storage, final IslandApi api, final int category) {
        this.plugin = plugin;
        this.storage = storage;
        this.api = api;
        this.category = category;
    }

    public void open(final @NotNull Player player) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final String title = category == 1 ? "Top - Solo" : (category == 2 ? "Top - Duo"
                : (category == 3 ? "Top - Team" : "Top - Core"));
        final InventoryGui gui = new InventoryGui(plugin, 6,
                "<white>" + title, Material.BLACK_STAINED_GLASS_PANE);

        final List<Island> top;
        if (category == 4) {
            // Core category: rank islands by their combined Core score
            // (Core money + Sky Tokens, weighted). Reuses the existing storage +
            // the Island Core service so no new leaderboard table is introduced.
            top = coreRanked(10);
        } else {
            top = storage.getTopIslands(category, 10);
        }
        int slot = 0;
        for (int rank = 1; rank <= 10; rank++) {
            final Island is = rank <= top.size() ? top.get(rank - 1) : null;
            if (is == null) {
                gui.setItem(slot, ItemUtil.create(Material.GRAY_DYE, "<gray>#" + rank, null), null);
            } else {
                final OfflinePlayer owner = org.bukkit.Bukkit.getOfflinePlayer(is.getOwner());
                final List<String> lore = new ArrayList<>();
                lore.add("<gray>Level: <white>" + FormatUtil.formatNumber(is.getLevel()));
                lore.add("<gray>XP: <white>" + FormatUtil.formatNumber(is.getXp()));
                lore.add("<gray>Members: <white>" + is.getMemberCount());
                if (category == 4) {
                    final var core = net.coremc.coremc.CoreMC.getInstance().islandCore();
                    if (core != null) {
                        lore.add("");
                        lore.add("<gray>Core Money: <gold>$"
                                + FormatUtil.formatNumber((long) core.service().islandMoney(is.getId())));
                        lore.add("<gray>Sky Tokens: <aqua>"
                                + FormatUtil.formatNumber(core.service().islandTokens(is.getId())));
                        lore.add("<gray>Progression: <yellow>"
                                + FormatUtil.formatNumber(core.service().islandProgression(is.getId())));
                    }
                }
                gui.setItem(slot, ItemUtil.create(Material.PLAYER_HEAD,
                                "<yellow>#" + rank + " <white>" + (owner.getName() == null ? "Unknown" : owner.getName()),
                                lore),
                        null);
            }
            slot += 2;
        }

        final Island mine = api.getIsland(player.getUniqueId());
        final int myRank = mine == null ? -1 : (category == 4 ? coreRank(mine) : storage.getRank(mine));
        gui.setItem(49, ItemUtil.create(Material.COMPASS,
                        "<aqua>Your position: <white>" + (myRank < 0 ? "unranked" : "#" + myRank),
                        mine == null ? List.of("<gray>You have no island.") : List.of(
                                "<gray>XP: <white>" + FormatUtil.formatNumber(mine.getXp()))),
                null);

        // Category navigation.
        gui.setItem(45, ItemUtil.create(Material.PAPER, "<yellow>Solo", null),
                ev -> new TopGui(plugin, storage, api, 1).open(player));
        gui.setItem(47, ItemUtil.create(Material.PAPER, "<yellow>Duo", null),
                ev -> new TopGui(plugin, storage, api, 2).open(player));
        gui.setItem(51, ItemUtil.create(Material.PAPER, "<yellow>Team", null),
                ev -> new TopGui(plugin, storage, api, 3).open(player));
        gui.setItem(53, ItemUtil.create(Material.PAPER, "<yellow>Core", null),
                ev -> new TopGui(plugin, storage, api, 4).open(player));

        gui.open(player);
    }

    /** All islands ranked by Core score (descending), limited to {@code limit}. */
    private List<Island> coreRanked(final int limit) {
        final var core = net.coremc.coremc.CoreMC.getInstance().islandCore();
        final List<Island> all = storage.getAllIslands();
        if (core != null) {
            all.sort((a, b) -> Double.compare(
                    core.service().islandCoreScore(b.getId()),
                    core.service().islandCoreScore(a.getId())));
        }
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    /** 1-based rank of an island in the Core category. */
    private int coreRank(final Island island) {
        final var core = net.coremc.coremc.CoreMC.getInstance().islandCore();
        if (core == null) return -1;
        final double my = core.service().islandCoreScore(island.getId());
        int rank = 1;
        for (final Island other : storage.getAllIslands()) {
            if (other.getId() == island.getId()) continue;
            if (core.service().islandCoreScore(other.getId()) > my) rank++;
        }
        return rank;
    }
}
