package net.coremc.tags.gui;

import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.tags.CoreTags;
import net.coremc.tags.api.CoreTagsApi;
import net.coremc.tags.manager.TagManager;
import net.coremc.tags.model.TagDef;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** /tags GUI: browse, equip/unequip, and (in store mode) buy tags. */
public final class TagsGui {

    private static final int PER_PAGE = 45; // 5 rows of slots in a 6-row gui (row 5 = nav)

    public static void open(final Player p) {
        open(p, 0);
    }

    public static void open(final Player p, final int page) {
        final TagManager mgr = CoreTags.getInstance().manager();
        final List<TagDef> all = mgr.catalogue();
        final int pages = Math.max(1, (int) Math.ceil((double) all.size() / PER_PAGE));
        final int pg = Math.max(0, Math.min(page, pages - 1));

        final InventoryGui gui = new InventoryGui(CoreTags.getInstance(), 6,
                CoreFoundation.getInstance().messages().getPrefix() + " <white>Tags", Material.BLACK_STAINED_GLASS_PANE);

        final int start = pg * PER_PAGE;
        final int end = Math.min(start + PER_PAGE, all.size());
        int slot = 0;
        for (int i = start; i < end; i++) {
            final TagDef def = all.get(i);
            final boolean owned = mgr.hasTag(p.getUniqueId(), def.id());
            final boolean active = def.id().equalsIgnoreCase(mgr.getActive(p.getUniqueId()));
            gui.setItem(slot++, buildItem(p, def, owned, active), e -> onClick(p, def, owned, active));
        }

        // Navigation
        if (pg > 0) {
            gui.setItem(45, ItemUtil.create(Material.ARROW, "<gray>Previous", null), e -> open(p, pg - 1));
        }
        gui.setItem(49, ItemUtil.create(Material.PAPER,
                "<white>Page " + (pg + 1) + "/" + pages, List.of("<gray>" + all.size() + " tags")), e -> {});
        if (pg < pages - 1) {
            gui.setItem(53, ItemUtil.create(Material.ARROW, "<gray>Next", null), e -> open(p, pg + 1));
        }
        gui.open(p);
    }

    private static ItemStack buildItem(final Player p, final TagDef def,
                                       final boolean owned, final boolean active) {
        final List<String> lore = new ArrayList<>();
        lore.add(def.gradientMiniMessage()); // preview line shows the gradient
        if (!def.description().isEmpty()) {
            lore.add("<gray>" + def.description());
        }
        if (!def.obtain().isEmpty()) {
            lore.add("<dark_gray>How to get: " + def.obtain());
        }
        if (active) {
            lore.add("<green>Equipped");
        } else if (owned) {
            lore.add("<yellow>Click to equip");
        } else {
            lore.add("<red>Locked");
        }
        final Material mat = active ? Material.LIME_DYE
                : (owned ? Material.NAME_TAG : Material.GRAY_DYE);
        return ItemUtil.create(mat, def.display(), lore);
    }

    private static void onClick(final Player p, final TagDef def, final boolean owned, final boolean active) {
        final CoreTagsApi api = CoreTags.getInstance().api();
        if (!owned) {
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix()
                    + " <red>You don't have the " + def.gradientMiniMessage() + " <red>tag.");
            return;
        }
        if (active) {
            api.clearActiveTag(p);
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix()
                    + " <gray>Unequipped " + def.gradientMiniMessage() + "<gray>.");
        } else {
            api.setActiveTag(p, def.id());
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix()
                    + " <green>Equipped " + def.gradientMiniMessage() + "<green>.");
        }
        open(p, currentPage(p));
    }

    // store-browse helper for the chatcolor "Tags" section
    public static void openStore(final Player p) {
        final TagManager mgr = CoreTags.getInstance().manager();
        final InventoryGui gui = new InventoryGui(CoreTags.getInstance(), 6,
                "<light_purple>Tags — Store", Material.BLACK_STAINED_GLASS_PANE);
        int slot = 0;
        for (final TagDef def : mgr.catalogue()) {
            if (!def.purchasable()) {
                continue;
            }
            final boolean owned = mgr.hasTag(p.getUniqueId(), def.id());
            final List<String> lore = new ArrayList<>();
            lore.add(def.gradientMiniMessage());
            if (!def.description().isEmpty()) {
                lore.add("<gray>" + def.description());
            }
            if (owned) {
                lore.add("<green>Owned");
            } else {
                lore.add("<yellow>Price: " + currency() + " " + def.price());
                lore.add("<green>Click to purchase");
            }
            final Material mat = owned ? Material.LIME_DYE : Material.NAME_TAG;
            final TagDef d = def;
            gui.setItem(slot++, ItemUtil.create(mat, def.display(), lore), e -> {
                if (owned) {
                    return;
                }
                purchase(p, d);
            });
        }
        gui.setItem(53, ItemUtil.create(Material.ARROW, "<gray>Back", null), e ->
                openChatColor(p));
        gui.open(p);
    }

    private static void purchase(final Player p, final TagDef def) {
        final CoreTags plugin = CoreTags.getInstance();
        if (!p.hasPermission("core.tags.use")) {
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix() + " <red>No permission.");
            return;
        }
        // Delegate the actual payment to CoreStore (no second economy in CoreTags).
        if (!BukkitAvailable()) {
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix() + " <red>Store unavailable.");
            return;
        }
        try {
            final org.bukkit.plugin.java.JavaPlugin store =
                    (org.bukkit.plugin.java.JavaPlugin) org.bukkit.Bukkit.getPluginManager().getPlugin("core-store");
            final Object apiObj = store.getClass().getMethod("api").invoke(store);
            // createPurchase(Player, Type.TAG, id, name, price, txId) then completePurchase
            final Class<?> typeEnum = Class.forName("net.coremc.store.api.Purchase$Type");
            final Object tagType = java.lang.Enum.valueOf((Class<java.lang.Enum>) typeEnum, "TAG");
            final java.lang.reflect.Method create = apiObj.getClass().getMethod(
                    "createPurchase", org.bukkit.entity.Player.class, typeEnum, String.class, String.class, double.class, String.class);
            final Object purchase = create.invoke(apiObj, p, tagType, def.id(), def.display(), def.price(),
                    "TAG-" + System.currentTimeMillis());
            final java.lang.reflect.Method complete = apiObj.getClass().getMethod(
                    "completePurchase", org.bukkit.entity.Player.class, purchase.getClass());
            complete.invoke(apiObj, p, purchase);
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix()
                    + " <green>You unlocked the " + def.gradientMiniMessage() + " <green>tag!");
            openStore(p);
        } catch (final Throwable t) {
            plugin.getLogger().warning("Tag purchase via CoreStore failed: " + t.getMessage());
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix() + " <red>Purchase failed.");
        }
    }

    private static boolean BukkitAvailable() {
        return org.bukkit.Bukkit.getPluginManager().isPluginEnabled("core-store");
    }

    private static String currency() {
        try {
            final org.bukkit.plugin.java.JavaPlugin store =
                    (org.bukkit.plugin.java.JavaPlugin) org.bukkit.Bukkit.getPluginManager().getPlugin("core-store");
            return (String) store.getConfig().getClass().getMethod("getString", String.class, String.class)
                    .invoke(store.getConfig(), "currency", "Credits");
        } catch (final Throwable t) {
            return "Credits";
        }
    }

    private static void openChatColor(final Player p) {
        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("core-chatcolor")) {
            try {
                final org.bukkit.plugin.java.JavaPlugin cc =
                        (org.bukkit.plugin.java.JavaPlugin) org.bukkit.Bukkit.getPluginManager().getPlugin("core-chatcolor");
                cc.getClass().getMethod("openTagsSection", org.bukkit.entity.Player.class).invoke(cc, p);
                return;
            } catch (final Throwable ignored) {
            }
        }
        open(p);
    }

    // crude page tracking: reopen page 0 for simplicity on re-render
    private static int currentPage(final Player p) {
        return 0;
    }
}
