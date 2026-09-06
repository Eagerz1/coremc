package net.coremc.skyblock.missions;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.storage.Island;
import net.coremc.skyblock.progression.ProgressionModule;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Clean /missions browser. One page per category (Mining, Farming, Fishing,
 * Logging, Slaying, Generators, Spawners, Island). Each mission shows its name
 * (BOLD title), description, progress / requirement, reward summary, and a
 * completion / claim state. Click a completed mission to claim its rewards.
 */
public final class MissionsGui {

    private final MissionManager missions;

    private static final String[] CATEGORIES = {
            "mining", "farming", "fishing", "logging", "slaying", "generators", "spawners", "island"
    };
    private static final String[] CATEGORY_TITLES = {
            "Mining", "Farming", "Fishing", "Logging", "Slaying", "Generators", "Spawners", "Island Progression"
    };
    private static final Material[] CATEGORY_ICONS = {
            Material.STONE_PICKAXE, Material.WHEAT, Material.FISHING_ROD, Material.OAK_LOG,
            Material.DIAMOND_SWORD, Material.HAY_BLOCK, Material.SPAWNER, Material.ENDER_PEARL
    };

    public MissionsGui(final MissionManager missions) {
        this.missions = missions;
    }

    /** Open the category-selecting main page. */
    public void open(final Player player) {
        final InventoryGui gui = new InventoryGui(CoreMC.getInstance(), 5,
                "<bold><white>Missions", Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < CATEGORIES.length; i++) {
            final int slot = 10 + (i % 7) + (i / 7) * 9;
            final String cat = CATEGORIES[i];
            final String title = CATEGORY_TITLES[i];
            final Material icon = CATEGORY_ICONS[i];
            final int total = countInCategory(cat);
            final int done = countDone(player.getUniqueId(), cat);
            gui.setItem(slot, ItemUtil.create(icon, "<bold><yellow>" + title,
                    List.of("<gray>Missions in this category: <white>" + total,
                            "<gray>Completed: <green>" + done + "/" + total,
                            "<aqua>Click to view.")),
                    e -> { e.setCancelled(true); openCategory(player, cat, title); });
        }
        gui.setItem(40, ItemUtil.create(Material.BARRIER, "<bold><gray>Close", List.of("<gray>Close this menu.")),
                e -> { e.setCancelled(true); player.closeInventory(); });
        gui.open(player);
    }

    private void openCategory(final Player player, final String category, final String title) {
        final UUID uuid = player.getUniqueId();
        final InventoryGui gui = new InventoryGui(CoreMC.getInstance(), 6,
                "<bold><white>" + title + " Missions", Material.BLACK_STAINED_GLASS_PANE);
        int slot = 0;
        for (final Mission m : missions.all()) {
            if (!m.category().name().equalsIgnoreCase(category)) continue;
            final long prog = missions.getProgress(uuid, m.id());
            final long req = m.requirement();
            final boolean complete = prog >= req;
            final boolean claimed = missions.isClaimed(uuid, m.id());
            final List<String> lore = new ArrayList<>();
            lore.add("<gray>" + m.description());
            lore.add("");
            lore.add("<gray>Progress: <white>" + prog + " / " + req);
            lore.add("<gray>Reward: " + rewardSummary(m));
            lore.add("");
            final Material icon;
            if (claimed && !m.repeatable()) {
                icon = Material.LIME_CONCRETE;
                lore.add("<green>Completed — claimed.");
            } else if (complete) {
                icon = Material.GOLD_INGOT;
                lore.add("<gold>Click to claim reward!");
            } else {
                icon = Material.GRAY_CONCRETE;
                lore.add("<gray>Keep going to complete this.");
            }
            gui.setItem(slot++, ItemUtil.create(icon, "<bold><white>" + m.name(), lore),
                    e -> {
                        e.setCancelled(true);
                        if (complete) {
                            if (missions.claim(player, m.id())) {
                                openCategory(player, category, title);
                            }
                        } else {
                            CoreFoundation.getInstance().messages().sendRaw(player,
                                    CoreFoundation.getInstance().messages().getPrefix()
                                            + " <gray>Progress: " + prog + " / " + req + " — keep going!");
                        }
                    });
        }
        gui.setItem(49, ItemUtil.create(Material.ARROW, "<bold><gray>Back", null),
                e -> { e.setCancelled(true); open(player); });
        gui.open(player);
    }

    private String rewardSummary(final Mission m) {
        final Mission.Reward r = m.reward();
        final List<String> parts = new ArrayList<>();
        if (r.money > 0) parts.add("$" + (long) r.money);
        if (r.tokens > 0) parts.add(r.tokens + " Sky Tokens");
        if (r.credits > 0) parts.add(r.credits + " Credits");
        if (r.islandXp > 0) parts.add(r.islandXp + " Island XP");
        if (r.skyKeys > 0) parts.add(r.skyKeys + " Sky Key");
        if (r.riverKeys > 0) parts.add(r.riverKeys + " River Key");
        if (r.crimsonKeys > 0) parts.add(r.crimsonKeys + " Crimson Key");
        if (r.voteKeys > 0) parts.add(r.voteKeys + " Vote Key");
        if (r.monthlyKeys > 0) parts.add(r.monthlyKeys + " Monthly Key");
        if (r.eventKeys > 0) parts.add(r.eventKeys + " Event Key");
        if (r.boostKeys > 0) parts.add(r.boostKeys + " Boost Key");
        for (final String it : r.items) parts.add(it);
        return parts.isEmpty() ? "<gray>none" : String.join(", ", parts);
    }

    private int countInCategory(final String category) {
        int n = 0;
        for (final Mission m : missions.all()) {
            if (m.category().name().equalsIgnoreCase(category)) n++;
        }
        return n;
    }

    private int countDone(final UUID uuid, final String category) {
        int n = 0;
        for (final Mission m : missions.all()) {
            if (m.category().name().equalsIgnoreCase(category)
                    && missions.isClaimed(uuid, m.id())) n++;
        }
        return n;
    }
}
