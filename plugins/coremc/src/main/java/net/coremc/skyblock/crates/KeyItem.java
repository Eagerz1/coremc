package net.coremc.skyblock.crates;

import net.coremc.foundation.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and recognises the seven physical CoreMC key items.
 * <p>Each key carries a PDC tag {@code coremc.key = <keyid>} so the crate system
 * (and the store bundle bridge) can identify it unambiguously without depending on
 * any external crate plugin. Titles are BOLD, normal Minecraft font, NO small caps,
 * NO unintended italics, coloured per-key. All keys use TRIPWIRE_HOOK as the base
 * material with enchantment glow visible on the stack.
 */
public final class KeyItem {
    private static final String PDC_KEY = "coremc.key";
    private final JavaPlugin plugin;

    public KeyItem(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Build a physical key item for the given key id with the configured count. */
    public ItemStack build(final KeyId id, final int amount) {
        final var cfg = plugin.getConfig();
        final String section = "crates.keys." + id.id();

        final String materialName = cfg.getString(section + ".material", defaultMaterial(id).name());
        Material material = defaultMaterial(id);
        try {
            material = Material.valueOf(materialName.toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            // keep the default material
        }

        final String colour = cfg.getString(section + ".colour", defaultColour(id)).toLowerCase(java.util.Locale.ROOT);
        final String name = cfg.getString(section + ".name", id.defaultName());

        // Build clean, premium lore
        final List<String> loreLines = new ArrayList<>();
        loreLines.add("<white>Contains:</white>");
        final List<String> contains = cfg.getStringList(section + ".contains");
        if (contains.isEmpty()) {
            loreLines.add("<white>• " + defaultContainsDisplay(id) + "</white>");
        } else {
            for (final String c : contains) {
                loreLines.add("<white>• " + c + "</white>");
            }
        }
        loreLines.add("");
        loreLines.add("<gray>Left Click → View Rewards</gray>");
        loreLines.add("<gray>Right Click → Open Crate</gray>");

        final String display = "<bold><" + colour + ">" + name + "</" + colour + "></bold>";

        final ItemStack item = ItemUtil.create(material, display, loreLines);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Enchantment glow - keep it visible on the stack
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            // Hide the actual enchantment details from lore
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            // Internal identifier - reliable persistent data container
            // Do NOT identify keys by display name, lore, or material
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, PDC_KEY),
                    PersistentDataType.STRING,
                    id.id());
            item.setItemMeta(meta);
        }
        item.setAmount(Math.max(1, Math.min(amount, 64)));
        return item;
    }

    /** True if the item is a CoreMC key (identified by PersistentDataContainer). */
    public boolean isKey(final ItemStack item) {
        return keyId(item) != null;
    }

    /** Return the key id of an item, or null if it is not a CoreMC key.
     * Uses PersistentDataContainer - NOT display name, lore, or material.
     */
    public KeyId keyId(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        final String tag = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, PDC_KEY), PersistentDataType.STRING);
        return tag == null ? null : KeyId.byId(tag);
    }

    private Material defaultMaterial(final KeyId id) {
        return switch (id) {
            case VOTE -> Material.TRIPWIRE_HOOK;
            case RIVER -> Material.TRIPWIRE_HOOK;
            case SKY -> Material.TRIPWIRE_HOOK;
            case CRIMSON -> Material.TRIPWIRE_HOOK;
            case BOOST -> Material.TRIPWIRE_HOOK;
            case EVENT -> Material.TRIPWIRE_HOOK;
            case MONTHLY -> Material.TRIPWIRE_HOOK;
        };
    }

    private String defaultColour(final KeyId id) {
        return switch (id) {
            case VOTE -> "green";
            case RIVER -> "aqua";
            case SKY -> "blue";
            case CRIMSON -> "red";
            case BOOST -> "light_purple";
            case EVENT -> "gold";
            case MONTHLY -> "dark_purple";
        };
    }

    private String defaultContainsDisplay(final KeyId id) {
        return switch (id) {
            case VOTE -> "Vote rewards";
            case RIVER -> "River rewards";
            case SKY -> "Sky rewards";
            case CRIMSON -> "Crimson rewards";
            case BOOST -> "Boost rewards";
            case EVENT -> "Event rewards";
            case MONTHLY -> "Monthly rewards";
        };
    }
}