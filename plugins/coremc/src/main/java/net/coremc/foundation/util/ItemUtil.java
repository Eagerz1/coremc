package net.coremc.foundation.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Item and lore construction helpers.
 * All CoreMC GUI items: BOLD titles, NO italics, normal Minecraft font.
 */
public final class ItemUtil {

    private ItemUtil() {}

    /** Bold titles, disable italics globally for CoreMC GUI items. */
    private static Component boldNoItalic(final String miniMessage) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(miniMessage)
                .decoration(TextDecoration.BOLD, TextDecoration.State.TRUE)
                .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Create an item stack from a MiniMessage display name and lore. Titles bold, no italics. */
    public static @NotNull ItemStack create(final @NotNull Material material,
                                           final @Nullable String displayName,
                                           final @Nullable List<String> lore) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (displayName != null) {
            meta.displayName(boldNoItalic(displayName));
        }
        if (lore != null) {
            final List<Component> lines = new java.util.ArrayList<>(lore.size());
            for (final String line : lore) {
                lines.add(boldNoItalic(line));
            }
            meta.lore(lines);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Create an item from a MiniMessage display name with no lore. */
    public static @NotNull ItemStack create(final @NotNull Material material,
                                            final @Nullable String displayName) {
        return create(material, displayName, null);
    }

    /** Create an item from MiniMessage strings with a glow. Titles bold, no italics. */
    public static @NotNull ItemStack createGlowing(final @NotNull Material material,
                                                   final @Nullable String displayName,
                                                   final @Nullable List<String> lore) {
        final ItemStack item = create(material, displayName, lore);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Set the MiniMessage lore of an existing item (replaces existing lore). Titles bold, no italics. */
    public static @NotNull ItemStack setLore(final @NotNull ItemStack item,
                                            final @NotNull List<String> lore) {
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        final List<Component> lines = new java.util.ArrayList<>(lore.size());
        for (final String line : lore) {
            lines.add(boldNoItalic(line));
        }
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    /** Append a MiniMessage lore line to an existing item. */
    public static @NotNull ItemStack addLore(final @NotNull ItemStack item,
                                             final @NotNull String line) {
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        final List<Component> lines = meta.lore() == null
                ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(meta.lore());
        lines.add(boldNoItalic(line));
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    /** Set the MiniMessage display name of an existing item. */
    public static @NotNull ItemStack setName(final @NotNull ItemStack item,
                                             final @NotNull String displayName) {
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(boldNoItalic(displayName));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Set a bold, non-italic, vanilla-font display name directly on an ItemMeta
     *  (used for physical items such as tools and spawners). */
    public static void setPlainName(final @NotNull ItemMeta meta, final @NotNull String displayName) {
        meta.displayName(boldNoItalic(displayName));
    }
}