package net.coremc.store.gui;

import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.FormatUtil;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.store.CoreStore;
import net.coremc.store.StoreProducts;
import net.coremc.store.api.CoreStoreApi;
import net.coremc.store.api.Purchase;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Store GUIs: main menu, category listings and purchase confirmation. */
public final class StoreGui {

    private static final NamespacedKey ACTION = new NamespacedKey("coremc", "store_action");
    private static final NamespacedKey DATA = new NamespacedKey("coremc", "store_data");

    private enum Act { KEYS, BUNDLES, RANKS, BUY_KEY, BUY_BUNDLE, BUY_RANK, CONFIRM, CANCEL, BACK }

    public static void openMain(final Player p) {
        final InventoryGui gui = new InventoryGui(CoreStore.getInstance(), 3,
                "<white>Store", Material.BLACK_STAINED_GLASS_PANE);
        gui.setItem(11, item(Material.NETHER_STAR,
                "<aqua><bold>Keys", List.of("<gray>Crate keys for rewards"), Act.KEYS, ""), e -> openKeys(p));
        gui.setItem(13, item(Material.BUNDLE,
                "<gold><bold>Bundles", List.of("<gray>Value bundles & packages"), Act.BUNDLES, ""), e -> openBundles(p));
        gui.setItem(15, item(Material.DIAMOND,
                "<light_purple><bold>Ranks", List.of("<gray>Permanent rank upgrades"), Act.RANKS, ""), e -> openRanks(p));
        gui.open(p);
    }

    public static void openKeys(final Player p) {
        final CoreStoreApi api = CoreStore.getInstance().api();
        final List<StoreProducts.KeyDef> keys = api.products().keys();
        final InventoryGui gui = new InventoryGui(CoreStore.getInstance(), 6,
                "<bold><aqua>Keys", Material.BLACK_STAINED_GLASS_PANE);

        // Header
        gui.setItem(4, ItemUtil.create(Material.NETHER_STAR, "<bold><aqua>Keys",
                List.of("<gray>Select a key to view its contents.")), e -> {});

        int slot = 10; // Row 2
        for (final StoreProducts.KeyDef k : keys) {
            final List<String> lore = new java.util.ArrayList<>();
            lore.add("<bold><white>Contains:");
            for (final String d : k.desc()) {
                lore.add("<white>• " + d);
            }
            lore.add("");
            lore.add("<bold><yellow>Price: <white>" + currency() + " " + FormatUtil.formatNumber((long) k.price()));
            lore.add("");
            if (canBuy(p, k.id())) {
                lore.add("<bold><green>Click to Purchase");
            } else {
                lore.add("<bold><red>Locked — Missing Permission");
            }

            final Material mat = getKeyMaterial(k.name());
            gui.setItem(slot, lockedItem(mat, "<bold><aqua>" + k.name(), lore, canBuy(p, k.id())),
                    e -> { if (canBuy(p, k.id())) confirm(p, Purchase.Type.KEY, k.id(), k.name(), k.price()); });
            slot = nextSlot(slot);
        }
        gui.setItem(49, item(Material.ARROW, "<bold><gray>Back", null, Act.BACK, ""), e -> openMain(p));
        gui.open(p);
    }

    public static void openBundles(final Player p) {
        final CoreStoreApi api = CoreStore.getInstance().api();
        final List<StoreProducts.BundleDef> bundles = api.products().bundles();
        final InventoryGui gui = new InventoryGui(CoreStore.getInstance(), 6,
                "<bold><gold>Bundles", Material.BLACK_STAINED_GLASS_PANE);

        gui.setItem(4, ItemUtil.create(Material.BUNDLE, "<bold><gold>Bundles",
                List.of("<gray>Value bundles & packages.")), e -> {});

        int slot = 10;
        for (final StoreProducts.BundleDef b : bundles) {
            final List<String> lore = new java.util.ArrayList<>();
            lore.add("<bold><white>Contains:");
            for (final String c : b.contents()) {
                lore.add("<white>• " + c);
            }
            lore.add("");
            lore.add("<bold><yellow>Price: <white>" + currency() + " " + FormatUtil.formatNumber((long) b.price()));
            lore.add("");
            if (canBuy(p, b.id())) {
                lore.add("<bold><green>Click to Purchase");
            } else {
                lore.add("<bold><red>Locked — Missing Permission");
            }

            gui.setItem(slot, lockedItem(Material.BUNDLE, "<bold><gold>" + b.name(), lore, canBuy(p, b.id())),
                    e -> { if (canBuy(p, b.id())) confirm(p, Purchase.Type.BUNDLE, b.id(), b.name(), b.price()); });
            slot = nextSlot(slot);
        }
        gui.setItem(49, item(Material.ARROW, "<bold><gray>Back", null, Act.BACK, ""), e -> openMain(p));
        gui.open(p);
    }

    public static void openRanks(final Player p) {
        final CoreStoreApi api = CoreStore.getInstance().api();
        final List<StoreProducts.RankDef> ranks = api.products().ranks();
        final InventoryGui gui = new InventoryGui(CoreStore.getInstance(), 6,
                "<bold><light_purple>Ranks", Material.BLACK_STAINED_GLASS_PANE);

        gui.setItem(4, ItemUtil.create(Material.DIAMOND, "<bold><light_purple>Ranks",
                List.of("<gray>Permanent rank upgrades")), e -> {});

        int slot = 10;
        for (final StoreProducts.RankDef r : ranks) {
            final Material dye = safeDye(r.dye());
            final List<String> lore = new java.util.ArrayList<>();
            lore.add("<bold><white>Perks:");
            for (final String perk : r.perks()) {
                lore.add("<white>• " + perk);
            }
            lore.add("");
            lore.add("<bold><yellow>Price: <white>" + currency() + " " + FormatUtil.formatNumber((long) r.price()));
            lore.add("");
            if (canBuy(p, r.id())) {
                lore.add("<bold><green>Click to Purchase");
            } else {
                lore.add("<bold><red>Locked — Missing Permission");
            }

            gui.setItem(slot, lockedItem(dye, "<bold><light_purple>" + r.name(), lore, canBuy(p, r.id())),
                    e -> { if (canBuy(p, r.id())) confirm(p, Purchase.Type.RANK, r.id(), r.name(), r.price()); });
            slot = nextSlot(slot);
        }
        gui.setItem(49, item(Material.ARROW, "<bold><gray>Back", null, Act.BACK, ""), e -> openMain(p));
        gui.open(p);
    }

    /**
     * Advance to the next product slot, leaving a one-column gap between items and
     * skipping the right-most column so products stay balanced and uncramped.
     */
    private static int nextSlot(int slot) {
        slot++;
        if (slot % 9 == 8) slot++; // leave a gap before the last column
        if (slot % 9 == 0) slot++; // never use the final column
        return slot;
    }

    private static void confirm(final Player p, final Purchase.Type type, final String id,
                               final String name, final double price) {
        final InventoryGui gui = new InventoryGui(CoreStore.getInstance(), 3,
                "<white><bold>Confirm Purchase", Material.BLACK_STAINED_GLASS_PANE);
        gui.setItem(11, item(Material.LIME_DYE,
                "<green><bold>Confirm", List.of("<gray>" + name, "<yellow><bold>Price: <white>" + currency() + " <yellow>" + FormatUtil.formatNumber((long) price)), Act.CONFIRM, id + "|" + type + "|" + name + "|" + price), e -> {
            final Purchase purchase = CoreStore.getInstance().api().createPurchase(p, type, id, name, price, "MC-" + System.currentTimeMillis());
            CoreStore.getInstance().api().completePurchase(p, purchase);
            p.closeInventory();
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix()
                    + " <green>Purchased <bold>" + name + " <green>for " + currency() + " " + FormatUtil.formatNumber((long) price) + "!");
        });
        gui.setItem(15, item(Material.RED_DYE, "<red><bold>Cancel", null, Act.CANCEL, ""), e -> openMain(p));
        gui.open(p);
    }

    private static ItemStack item(final Material mat, final String name, final List<String> lore,
                                  final Act act, final String data) {
        final ItemStack stack = ItemUtil.create(mat, name, lore);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ACTION, PersistentDataType.STRING, act.name());
            meta.getPersistentDataContainer().set(DATA, PersistentDataType.STRING, data);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Material getKeyMaterial(final String keyName) {
        final String lower = keyName.toLowerCase();
        if (lower.contains("sky")) return Material.FEATHER;
        if (lower.contains("nether")) return Material.NETHERRACK;
        if (lower.contains("mythic")) return Material.NETHER_STAR;
        if (lower.contains("vote")) return Material.PAPER;
        if (lower.contains("river")) return Material.WATER_BUCKET;
        if (lower.contains("boost")) return Material.BLAZE_ROD;
        if (lower.contains("crimson")) return Material.NETHERITE_SCRAP;
        if (lower.contains("event")) return Material.FIREWORK_ROCKET;
        if (lower.contains("autumn")) return Material.ORANGE_DYE;
        return Material.TRIPWIRE_HOOK;
    }

    private static Material safeDye(final String name) {
        try {
            return Material.valueOf(name.toUpperCase(java.util.Locale.ROOT) + "_DYE");
        } catch (final IllegalArgumentException e) {
            return Material.WHITE_DYE;
        }
    }

    private static String currency() {
        return CoreStore.getInstance().getConfig().getString("currency", "Credits");
    }

    /** Whether the player may buy a product (per-product gate or always-on). */
    private static boolean canBuy(final Player p, final String id) {
        final String tpl = CoreStore.getInstance().getConfig().getString("buy-permission", "core.store.buy.{id}");
        if (tpl == null || tpl.isBlank()) {
            return true;
        }
        return p.hasPermission(tpl.replace("{id}", id)) || p.isOp();
    }

    /** Build an item; locked products are greyed and marked unbuyable. */
    private static ItemStack lockedItem(final Material mat, final String name,
                                        final List<String> lore, final boolean unlocked) {
        if (unlocked) {
            return item(mat, name, lore, Act.BUY_KEY, "");
        }
        final ItemStack locked = ItemUtil.create(Material.GRAY_DYE, "<dark_gray>" + name, lore);
        final ItemMeta meta = locked.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ACTION, PersistentDataType.STRING, Act.BACK.name());
            locked.setItemMeta(meta);
        }
        return locked;
    }
}
