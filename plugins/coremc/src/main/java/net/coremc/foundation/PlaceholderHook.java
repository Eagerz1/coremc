package net.coremc.foundation;
import net.coremc.foundation.CoreFoundation;
import org.bukkit.plugin.java.JavaPlugin;

import net.coremc.foundation.util.ColorUtil;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Optional PlaceholderAPI integration. If PlaceholderAPI is present on the server,
 * registered placeholders are expanded via {@link #set(String, PlaceholderFunction)}.
 * If it is absent, {@link #apply(String, OfflinePlayer)} simply returns the input
 * unchanged so dependent plugins can keep using the same code path.
 */
public final class PlaceholderHook {

    private static boolean registered = false;

    @FunctionalInterface
    public interface PlaceholderFunction {
        @Nullable String apply(@Nullable OfflinePlayer player);
    }

    private static final java.util.Map<String, PlaceholderFunction> REGISTRY = new java.util.concurrent.ConcurrentHashMap<>();

    /** Return the current placeholder registry (may be empty if PAPI not available). */
    public static java.util.Map<String, PlaceholderFunction> getRegistry() {
        return REGISTRY;
    }

    private PlaceholderHook() {}

    /** Register the bridge if PlaceholderAPI is loaded. Called once on enable. */
    public static void register(final JavaPlugin plugin) {
        if (registered) {
            return;
        }
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
        } catch (final ClassNotFoundException e) {
            CoreFoundation.getInstance().debug("PlaceholderAPI not found - placeholders disabled.");
            return;
        }
        try {
            new PlaceholderExpansionBridge(plugin, REGISTRY).register();
            registered = true;
            CoreFoundation.getInstance().debug("PlaceholderAPI hook registered.");
        } catch (final Throwable t) {
            plugin.getLogger().warning("Failed to register PlaceholderAPI hook: " + t.getMessage());
        }
    }

    /** Register a custom placeholder, e.g. {@code %coremc_test%}. */
    public static void set(final @NotNull String identifier, final @NotNull PlaceholderFunction fn) {
        REGISTRY.put(identifier.toLowerCase(java.util.Locale.ROOT), fn);
    }

    /** Expand every {@code %coremc_xxx%} token in the input; returns input unchanged if no PAPI. */
    public static @NotNull String apply(@NotNull String input, final @Nullable OfflinePlayer player) {
        if (input == null) {
            return input;
        }
        // Always expand our own registered placeholders locally so that a TAB
        // / player-list listener works even without PlaceholderAPI present.
        for (final var entry : REGISTRY.entrySet()) {
            final String token = "%coremc_" + entry.getKey() + "%";
            if (input.contains(token)) {
                final String value = entry.getValue().apply(player);
                input = input.replace(token, value == null ? "" : value);
            }
        }
        return input;
    }

    /** Convenience: parse MiniMessage strings that may contain CoreMC placeholders. */
    public static net.kyori.adventure.text.Component parse(@NotNull String input, final @Nullable OfflinePlayer player) {
        return ColorUtil.parse(apply(input, player));
    }
}
