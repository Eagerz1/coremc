package net.coremc.skyblock.crates.rarity;

import net.coremc.foundation.util.ColorUtil;
import org.bukkit.Color;

/**
 * CoreMC lootbox rarity tiers, ordered low to high.
 *
 * <p>Each rarity has a configurable MiniMessage colour (read from config at lookup time),
 * a default legacy colour for particle fallbacks, and a weight tier used by the lootbox
 * chance validation. Rarities are:
 * <ol>
 *   <li>COMMON</li>
 *   <li>UNCOMMON</li>
 *   <li>RARE</li>
 *   <li>EPIC</li>
 *   <li>LEGENDARY</li>
 *   <li>MYTHIC</li>
 * </ol>
 */
public enum Rarity {

    COMMON(0, "white",       "#ffffff"),
    UNCOMMON(1, "yellow",     "#ffff55"),
    RARE(2, "blue",          "#55aaff"),
    EPIC(3, "dark_purple",   "#aa55ff"),
    LEGENDARY(4, "gold",      "#ffaa00"),
    MYTHIC(5, "light_purple", "#ff55ff");

    private final int order;
    private final String defaultColor;
    private final String defaultHex;

    Rarity(final int order, final String defaultColor, final String defaultHex) {
        this.order = order;
        this.defaultColor = defaultColor;
        this.defaultHex = defaultHex;
    }

    /** Sort order index (0 = lowest). */
    public int order() {
        return order;
    }

    /** Default Bukkit/MiniMessage colour name for this rarity. */
    public String colorName() {
        return defaultColor;
    }

    /** Default hex colour for this rarity (particles, effects). */
    public String hex() {
        return defaultHex;
    }

    /** {@link Color} for Bukkit particle APIs. */
    public Color particleColor() {
        try {
            return Color.fromRGB(Integer.parseInt(defaultHex.replace("#", ""), 16));
        } catch (final NumberFormatException e) {
            return Color.WHITE;
        }
    }

    /**
     * Resolve the MiniMessage colour name for this rarity from config, falling back
     * to the built-in default. Looks under {@code lootbox.rarity-colours.<RARITY>}.
     */
    public String configuredColor(final org.bukkit.plugin.java.JavaPlugin plugin) {
        final String key = "lootbox.rarity-colours." + name();
        final String val = plugin.getConfig().getString(key, defaultColor);
        return val == null || val.isBlank() ? defaultColor : val;
    }

    /**
     * Build a MiniMessage gradient string for rarity-labelled text.
     * e.g. {@code gradientText("MYTHIC", "gold", "yellow")} -> {@code <gradient:gold:yellow>MYTHIC</gradient>}.
     */
    public String colouredLabel(final org.bukkit.plugin.java.JavaPlugin plugin) {
        final String c = configuredColor(plugin);
        return "<" + c + ">" + name().charAt(0) + name().substring(1).toLowerCase() + "</" + c + ">";
    }

    /** Parse a rarity name case-insensitively; returns {@code null} if unknown. */
    public static Rarity byName(final String name) {
        if (name == null) return null;
        for (final Rarity r : values()) {
            if (r.name().equalsIgnoreCase(name)) return r;
        }
        return null;
    }
}
