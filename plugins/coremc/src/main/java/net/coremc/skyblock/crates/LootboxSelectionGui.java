package net.coremc.skyblock.crates;

import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.skyblock.crates.config.LootboxConfig;
import net.coremc.skyblock.crates.rarity.Rarity;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Premium lootbox selection menu.
 *
 * <p>Displays each available lootbox as a large visual card with:
 * <ul>
 *   <li>The lootbox item (custom material/coloured) as a prominent icon.</li>
 *   <li>A clean BOLD display name.</li>
 *   <li>The description/subtitle line.</li>
 *   <li>A rarity-tier summary (Common x8, Rare x4, etc.).</li>
 *   <li>Total reward count.</li>
 *   <li>Two action buttons: [ VIEW REWARDS ] and [ OPEN LOOTBOX ].</li>
 * </ul>
 *
 * <p>Layout uses a 6-row chest GUI (54 slots). Each lootbox occupies a card area
 * of the inventory. Filler panes frame the cards. The design avoids the generic
 * chest-GUI look by using coloured borders, stained glass, and structured card
 * layouts.
 */
public final class LootboxSelectionGui {

    private final JavaPlugin plugin;
    private final LootboxManager manager;

    public LootboxSelectionGui(final JavaPlugin plugin, final LootboxManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /** Open the selection menu for a player. */
    public void open(final Player p) {
        final Set<String> ids = manager.ids();
        final int rows = 6;
        final InventoryGui gui = new InventoryGui(plugin, rows,
                "<bold><dark_purple>CoreMC Lootboxes</dark_purple></bold>",
                Material.BLACK_STAINED_GLASS_PANE);

        // Build a frame using coloured stained glass for a premium border.
        final ItemStack border = ItemUtil.create(Material.PURPLE_STAINED_GLASS_PANE,
                "<bold><dark_purple></dark_purple></bold>", null);
        final ItemStack innerBorder = ItemUtil.create(Material.BLACK_STAINED_GLASS_PANE, " ", null);

        // Draw the outer border.
        for (int i = 0; i < 54; i++) {
            if (isBorder(i)) {
                gui.setItem(i, border);
            } else {
                gui.setItem(i, innerBorder);
            }
        }

        // Title slot.
        gui.setItem(10, ItemUtil.create(Material.ENDER_EYE,
                "<bold><gold>Lootboxes</gold></bold>",
                List.of("<gray>Select a lootbox to view and open.</gray>")));

        // Determine card positions. Each card takes a 7-wide area.
        // Cards are placed in rows of 3 cards (columns at slots 12, 21, 30, 39, 48 for 5 cards max,
        // but we'll use a 2x3 grid: 6 cards max in a 54-slot GUI).
        // Card top-left corners: 12, 14, 21, 23, 30, 32
        final int[] cardStarts = {12, 14, 21, 23, 30, 32};

        int cardIdx = 0;
        for (final String id : ids) {
            if (cardIdx >= cardStarts.length) break;
            final LootboxConfig box = manager.get(id);
            if (box == null) continue;

            final int start = cardStarts[cardIdx];
            drawCard(gui, start, box, p);
            cardIdx++;
        }

        // If no lootboxes found, show a message.
        if (ids.isEmpty()) {
            gui.setItem(22, ItemUtil.create(Material.BARRIER,
                    "<bold><red>No lootboxes found</red></bold>",
                    List.of("<gray>Configure lootboxes in the lootboxes/ folder.</gray>")));
        }

        gui.open(p);
    }

    /**
     * Draw a single lootbox card starting at the given slot.
     * Card layout (7 slots wide, 3 rows tall):
     *
     * <pre>
     * [B][L][L][L][L][L][B]   — top border + name
     * [B][ ][I][ ][A][ ][B]   — icon + action button
     * [B][L][L][I][L][A][B]   — lore + preview + open
     * </pre>
     * Where B=border, L=lore lines, I=icon, A=action buttons.
     */
    private void drawCard(final InventoryGui gui, final int start, final LootboxConfig box, final Player p) {
        final String colour = box.iconColour();
        final int row1 = start;         // top border
        final int row2 = start + 9;     // middle
        final int row3 = start + 18;    // bottom

        // --- Row 1: Top border + name ---
        for (int i = 0; i < 7; i++) {
            gui.setItem(row1 + i, cardBorder(colour, box.displayName(), box));
        }

        // --- Row 2: Icon on left, action button ---
        final int iconSlot = row2 + 2;
        final int actionSlot = row2 + 4;
        gui.setItem(iconSlot, box.buildCardItem());

        gui.setItem(actionSlot, ItemUtil.create(Material.LIME_DYE,
                "<bold><green>[ VIEW REWARDS ]</green></bold>",
                List.of("<gray>Preview all possible rewards.</gray>")),
                ev -> {
                    final Player clicker = (Player) ev.getWhoClicked();
                    clicker.closeInventory();
                    manager.openPreview(clicker, box.id());
                });

        // --- Row 3: Description + rarity summary ---
        gui.setItem(row3 + 1, ItemUtil.create(Material.PAPER,
                "<bold><" + colour + ">" + box.displayName() + "</" + colour + "></bold>",
                buildCardLore(box)));

        gui.setItem(row3 + 3, ItemUtil.create(Material.ORANGE_DYE,
                "<bold><" + colour + ">[ OPEN LOOTBOX ]</" + colour + "></bold>",
                List.of("<gray>Click to open this lootbox.</gray>")),
                ev -> {
                    final Player clicker = (Player) ev.getWhoClicked();
                    clicker.closeInventory();
                    manager.openById(clicker, box);
                });

        // Fill remaining card slots with subtle border.
        for (int i = 0; i < 7; i++) {
            final int slot = row3 + i;
            gui.setItem(slot, cardBorderLight(colour));
        }
        // Fill row 2 remaining.
        for (int i = 0; i < 7; i++) {
            final int slot = row2 + i;
            if (slot == iconSlot || slot == actionSlot) continue;
            gui.setItem(slot, cardBorderLight(colour));
        }
    }

    private List<String> buildCardLore(final LootboxConfig box) {
        final List<String> lore = new ArrayList<>();
        final String colour = box.iconColour();

        if (box.description() != null && !box.description().isEmpty()) {
            lore.add("<gray>" + box.description() + "</gray>");
        }
        lore.add("");
        lore.add("<gray>Rarity tiers: " + raritySummary(box) + "</gray>");
        lore.add("<gray>Total rewards: " + box.rewards().size() + "</gray>");
        if (box.hasPermission()) {
            lore.add("<gray>Permission: " + box.permission() + "</gray>");
        }
        lore.add("");
        lore.add("<" + colour + ">Right-click to preview.</" + colour + ">");
        lore.add("<" + colour + ">Place or click OPEN to open.</" + colour + ">");
        return lore;
    }

    private String raritySummary(final LootboxConfig box) {
        final Map<Rarity, Integer> counts = new EnumMap<>(Rarity.class);
        for (final net.coremc.skyblock.crates.config.LootboxConfig.Reward r : box.rewards()) {
            counts.merge(r.rarity(), 1, Integer::sum);
        }
        final StringBuilder sb = new StringBuilder();
        for (final Rarity r : Rarity.values()) {
            final Integer c = counts.get(r);
            if (c != null && c > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(r.name().charAt(0)).append(r.name().substring(1).toLowerCase())
                        .append(" x").append(c);
            }
        }
        return sb.toString();
    }

    private ItemStack cardBorder(final String colour, final String name, final LootboxConfig box) {
        // Use a banner or stained glass pane as the card border header.
        return ItemUtil.create(Material.GRAY_STAINED_GLASS_PANE,
                "<bold><" + colour + ">" + name + "</" + colour + "></bold>",
                List.of("<gray>────────────────</gray>"));
    }

    private ItemStack cardBorderLight(final String colour) {
        return ItemUtil.create(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", null);
    }

    private boolean isBorder(final int slot) {
        // Outer border: top row (0-8), bottom row (45-53), left column (0,9,18,27,36,45),
        // right column (8,17,26,35,44,53).
        return slot < 9 || slot > 44 || slot % 9 == 0 || slot % 9 == 8;
    }
}
