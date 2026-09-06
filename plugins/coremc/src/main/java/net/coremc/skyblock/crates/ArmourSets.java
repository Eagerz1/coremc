package net.coremc.skyblock.crates;

import net.coremc.foundation.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds, displays and grants full CoreMC armour sets.
 *
 * <p>A set is defined in config under {@code crates.armour.<setId>} with a
 * {@code material} (base, e.g. IRON/DIAMOND/NETHERITE), a {@code colour} for the
 * bold display name, an optional list of {@code enchants} ({@code ENCHANT:level}),
 * and {@code unbreakable}. When granted, the player receives the FULL set:
 * helmet, chestplate, leggings and boots — never a single piece.</p>
 */
public final class ArmourSets {

    private static final String PDC_SET = "coremc.armour";
    private final JavaPlugin plugin;

    public ArmourSets(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Grant the full set to a player (all four pieces). */
    public void grantSet(final Player p, final String setId) {
        final var cfg = plugin.getConfig();
        final var sec = cfg.getConfigurationSection("crates.armour." + setId);
        if (sec == null) {
            plugin.getLogger().warning("[crates] missing armour set: " + setId);
            return;
        }
        for (final Piece piece : Piece.values()) {
            final ItemStack it = buildPiece(sec, piece);
            if (it != null) {
                final var left = p.getInventory().addItem(it);
                for (final ItemStack drop : left.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
            }
        }
    }

    /** Display item representing the set (chestplate piece, for GUIs / animations). */
    public ItemStack displaySet(final String setId) {
        final var cfg = plugin.getConfig();
        final var sec = cfg.getConfigurationSection("crates.armour." + setId);
        if (sec == null) {
            return net.coremc.foundation.util.ItemUtil.create(Material.BARRIER,
                    "<red>Unknown Set</red>", List.of());
        }
        final ItemStack it = buildPiece(sec, Piece.CHESTPLATE);
        return it == null ? net.coremc.foundation.util.ItemUtil.create(Material.BARRIER,
                "<red>Unknown Set</red>", List.of()) : it;
    }

    private ItemStack buildPiece(final org.bukkit.configuration.ConfigurationSection sec,
                                 final Piece piece) {
        // Crate armour is dyed LEATHER so each set has a distinct, visible colour.
        final Material material;
        try {
            material = Material.valueOf("LEATHER_" + piece.suffix());
        } catch (final IllegalArgumentException e) {
            plugin.getLogger().warning("[crates] bad armour piece: " + piece.suffix());
            return null;
        }
        final String colour = sec.getString("colour", "white");
        final String name = sec.getString("name", sec.getName());
        final ItemStack it = new ItemStack(material);
        final ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            // Bold, coloured display name (same pipeline as the rest of CoreMC).
            meta.displayName(net.coremc.foundation.util.ColorUtil.parse(
                    "<bold><" + colour + ">" + name + " " + piece.label() + "</" + colour + "></bold>"));
            // Dye the leather to the set colour.
            if (meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta lam) {
                lam.setColor(dyeColour(colour));
            }
            // enchants
            final List<String> enchants = sec.getStringList("enchants");
            for (final String e : enchants) {
                final int colon = e.indexOf(':');
                final String en = (colon > 0 ? e.substring(0, colon) : e).trim();
                final int lvl = colon > 0 ? safeInt(e.substring(colon + 1).trim(), 1) : 1;
                try {
                    final Enchantment ench = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(en.toLowerCase()));
                    if (ench != null) {
                        meta.addEnchant(ench, lvl, true);
                    }
                } catch (final IllegalArgumentException ignore) {}
            }
            if (sec.getBoolean("unbreakable", false)) {
                meta.setUnbreakable(true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, PDC_SET), PersistentDataType.STRING, sec.getName());
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Map a MiniMessage colour name to a Bukkit leather dye colour. */
    private org.bukkit.Color dyeColour(final String colour) {
        return switch (colour.toLowerCase(java.util.Locale.ROOT)) {
            case "black" -> org.bukkit.Color.BLACK;
            case "dark_blue" -> org.bukkit.Color.fromRGB(0x1a1aff);
            case "dark_green" -> org.bukkit.Color.fromRGB(0x206020);
            case "dark_aqua" -> org.bukkit.Color.fromRGB(0x0f9fa0);
            case "dark_red" -> org.bukkit.Color.fromRGB(0xaa0000);
            case "dark_purple" -> org.bukkit.Color.fromRGB(0x6a0dad);
            case "gold" -> org.bukkit.Color.fromRGB(0xffd700);
            case "gray", "grey" -> org.bukkit.Color.fromRGB(0x9a9a9a);
            case "dark_gray", "dark_grey" -> org.bukkit.Color.fromRGB(0x4a4a4a);
            case "blue" -> org.bukkit.Color.fromRGB(0x3366ff);
            case "green" -> org.bukkit.Color.fromRGB(0x2ecc40);
            case "aqua" -> org.bukkit.Color.fromRGB(0x2ee6e6);
            case "red" -> org.bukkit.Color.fromRGB(0xff2020);
            case "light_purple", "pink" -> org.bukkit.Color.fromRGB(0xc95ce6);
            case "yellow" -> org.bukkit.Color.fromRGB(0xffee55);
            case "white" -> org.bukkit.Color.WHITE;
            default -> org.bukkit.Color.WHITE;
        };
    }

    /** Grant a pre-built item stack of the full set (used by lootbox animation hand-off). */
    public List<ItemStack> buildFullSet(final String setId) {
        final var cfg = plugin.getConfig();
        final var sec = cfg.getConfigurationSection("crates.armour." + setId);
        final List<ItemStack> out = new ArrayList<>();
        if (sec == null) return out;
        for (final Piece piece : Piece.values()) {
            final ItemStack it = buildPiece(sec, piece);
            if (it != null) out.add(it);
        }
        return out;
    }

    private int safeInt(final String s, final int def) {
        try { return Integer.parseInt(s); } catch (final NumberFormatException e) { return def; }
    }

    private enum Piece {
        HELMET("HELMET", "Helmet"),
        CHESTPLATE("CHESTPLATE", "Chestplate"),
        LEGGINGS("LEGGINGS", "Leggings"),
        BOOTS("BOOTS", "Boots");

        private final String suffix;
        private final String label;

        Piece(final String suffix, final String label) {
            this.suffix = suffix;
            this.label = label;
        }

        public String suffix() { return suffix; }
        public String label() { return label; }
    }
}
