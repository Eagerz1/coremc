package net.coremc.foundation.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Colour / gradient / formatting helpers built on Adventure's MiniMessage.
 */
public final class ColorUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ColorUtil() {}

    /** Parse a MiniMessage string into a Component. */
    public static @NotNull Component parse(final @NotNull String input) {
        return MM.deserialize(input);
    }

    /** Parse MiniMessage and substitute a {@code <prefix>} token. */
    public static @NotNull Component parse(final @NotNull String input, final @NotNull String prefix) {
        return MM.deserialize(input.replace("<prefix>", prefix));
    }

    /**
     * Generate a smooth hex gradient between two colours over {@code steps} stops.
     * @return a list of 6-digit hex strings (e.g. {@code #00eaff}).
     */
    public static @NotNull List<String> gradient(final @NotNull String fromHex,
                                                 final @NotNull String toHex,
                                                 final int steps) {
        final Color from = Color.decode(fromHex.startsWith("#") ? fromHex : "#" + fromHex);
        final Color to = Color.decode(toHex.startsWith("#") ? toHex : "#" + toHex);
        final List<String> out = new ArrayList<>(Math.max(steps, 2));
        for (int i = 0; i < steps; i++) {
            final double t = steps <= 1 ? 0.0 : (double) i / (steps - 1);
            final int r = (int) (from.getRed() + (to.getRed() - from.getRed()) * t);
            final int g = (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * t);
            final int b = (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * t);
            out.add(String.format("#%02x%02x%02x", r, g, b));
        }
        return out;
    }

    /** Build a MiniMessage gradient string across the given text. */
    public static @NotNull String gradientText(final @NotNull String text,
                                               final @NotNull String fromHex,
                                               final @NotNull String toHex) {
        return "<gradient:" + fromHex + ":" + toHex + ">" + text + "</gradient>";
    }

    /** Build a {@code <color:#hex>} tag. */
    public static @NotNull String colorTag(final @NotNull String hex) {
        return "<color:" + (hex.startsWith("#") ? hex : "#" + hex) + ">";
    }

    /** Convert an AWT Color to a 6-digit hex string. */
    public static @NotNull String toHex(final @NotNull Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
