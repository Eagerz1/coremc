package net.coremc.skyblock.crates;

import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.skyblock.crates.config.LootboxConfig;
import net.coremc.skyblock.crates.config.LootboxConfig.Reward;
import net.coremc.skyblock.crates.rarity.Rarity;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Premium reward preview GUI for a lootbox.
 *
 * <p>Organizes all possible rewards by rarity tier:
 * <pre>
 *   COMMON
 *   [ ] [ ] [ ] [ ]
 *
 *   UNCOMMON
 *   [ ] [ ] [ ]
 *
 *   RARE
 *   [ ] [ ] [ ]
 *
 *   EPIC
 *   [ ] [ ] [ ]
 *
 *   LEGENDARY
 *   [ ] [ ] [ ]
 *
 *   MYTHIC
 *   [ ] [ ]
 * </pre>
 *
 * <p>Each reward shows: icon, name, rarity, and chance.
 * The menu is clean and easy to understand — rewards are grouped into rarity
 * sections with a header row showing the rarity name and colour.
 */
public final class LootboxPreviewGui {

    private final JavaPlugin plugin;
    private final LootboxManager manager;
    private final LootboxConfig box;
    private final RewardResolver resolver;

    public LootboxPreviewGui(final JavaPlugin plugin, final LootboxManager manager,
                             final LootboxConfig box, final RewardResolver resolver) {
        this.plugin = plugin;
        this.manager = manager;
        this.box = box;
        this.resolver = resolver;
    }

    /** Open the preview GUI for a player. */
    public void open(final Player p) {
        final int rows = 6;
        final InventoryGui gui = new InventoryGui(plugin, rows,
                "<bold><" + box.iconColour() + ">" + box.displayName()
                        + " — Rewards</" + box.iconColour() + "></bold>",
                Material.BLACK_STAINED_GLASS_PANE);

        // Fill with dark filler.
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, ItemUtil.create(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }

        // Back button.
        gui.setItem(45, ItemUtil.create(Material.ARROW,
                "<bold><gray>← Back</gray></bold>",
                List.of("<gray>Return to lootbox selection.</gray>")),
                ev -> {
                    final Player clicker = (Player) ev.getWhoClicked();
                    clicker.closeInventory();
                    manager.openSelectionGui(clicker);
                });

        // Group rewards by rarity.
        final Map<Rarity, List<Reward>> byRarity = new EnumMap<>(Rarity.class);
        for (final Reward r : box.rewards()) {
            byRarity.computeIfAbsent(r.rarity(), k -> new ArrayList<>()).add(r);
        }

        // Starting slot for each rarity section.
        int slot = 11; // row 1 starts at 9, leave 10 for spacing

        for (final Rarity rarity : Rarity.values()) {
            final List<Reward> rewards = byRarity.get(rarity);
            if (rewards == null || rewards.isEmpty()) continue;

            // Header item showing rarity name + colour.
            final String colour = rarity.configuredColor(plugin);
            gui.setItem(slot, rarityHeader(rarity, colour, rewards.size()));
            slot++;

            // Reward items in a row of up to 7.
            int rowSlot = slot;
            for (int i = 0; i < rewards.size(); i++) {
                final Reward r = rewards.get(i);
                if (rowSlot % 9 == 8) {
                    // Wrap to next row.
                    rowSlot += 9 - (rowSlot % 9);
                    rowSlot += 2; // skip the left border
                }
                gui.setItem(rowSlot, r.displayItem(plugin, resolver), ev -> {
                    // Show detailed info on click.
                    final Player clicker = (Player) ev.getWhoClicked();
                    showRewardDetails(clicker, r);
                });
                rowSlot++;
                if (rowSlot % 9 >= 7) { // reached right border area
                    rowSlot += 2; // skip right border
                }
            }

            // Move to next section (skip one row gap).
            slot = rowSlot;
            if (slot % 9 > 1) {
                slot += 9 - (slot % 9);
                slot += 2; // left margin
            }
            if (slot >= 45) break; // ran out of space
        }

        gui.open(p);
    }

    /** Show detailed reward info in a small popup GUI. */
    private void showRewardDetails(final Player p, final Reward r) {
        final InventoryGui detailGui = new InventoryGui(plugin, 3,
                "<bold>" + r.display() + "</bold>", Material.BLACK_STAINED_GLASS_PANE);

        // Center the reward item.
        detailGui.setItem(13, r.displayItem(plugin, resolver));

        // Info lines around it.
        final String colour = r.raretyColour().isEmpty()
                ? r.rarity().configuredColor(plugin) : r.raretyColour();

        detailGui.setItem(4, ItemUtil.create(Material.NAME_TAG,
                "<bold>Rarity:</bold>",
                List.of("<" + colour + r.rarity().name() + "</" + colour + ">")));

        detailGui.setItem(3, ItemUtil.create(Material.PAPER,
                "<bold>Chance:</bold>",
                List.of("<gray>" + String.format(java.util.Locale.ROOT, "%.2f", r.chance()) + "%</gray>")));

        detailGui.setItem(5, ItemUtil.create(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                "<bold>Type:</bold>",
                List.of("<gray>" + r.type() + "</gray>")));

        if (r.description() != null && !r.description().isEmpty()) {
            detailGui.setItem(22, ItemUtil.create(Material.WRITABLE_BOOK,
                    "<bold>Description:</bold>",
                    List.of("<gray>" + r.description() + "</gray>")));
        }

        // Close button.
        detailGui.setItem(22, ItemUtil.create(Material.BARRIER,
                "<red>Close", List.of("<gray>Click to go back.</gray>")),
                ev -> p.closeInventory());

        detailGui.open(p);
    }

    private ItemStack rarityHeader(final Rarity rarity, final String colour, final int count) {
        final String name = rarity.name();
        return ItemUtil.create(getRarityMaterial(rarity),
                "<bold><" + colour + ">" + name + "</" + colour + "></bold>",
                List.of("<gray>" + count + " reward" + (count == 1 ? "" : "s") + "</gray>"));
    }

    private Material getRarityMaterial(final Rarity rarity) {
        return switch (rarity) {
            case COMMON -> Material.WHITE_DYE;
            case UNCOMMON -> Material.YELLOW_DYE;
            case RARE -> Material.BLUE_DYE;
            case EPIC -> Material.PURPLE_DYE;
            case LEGENDARY -> Material.GOLD_INGOT;
            case MYTHIC -> Material.AMETHYST_SHARD;
        };
    }
}
