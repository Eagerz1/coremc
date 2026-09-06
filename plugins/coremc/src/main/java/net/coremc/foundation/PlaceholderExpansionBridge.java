package net.coremc.foundation;
import org.bukkit.plugin.java.JavaPlugin;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Internal PAPI expansion that bridges registered CoreMC placeholders.
 */
class PlaceholderExpansionBridge extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Map<String, PlaceholderHook.PlaceholderFunction> registry;

    PlaceholderExpansionBridge(final JavaPlugin plugin,
                               final Map<String, PlaceholderHook.PlaceholderFunction> registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "coremc";
    }

    @Override
    public @NotNull String getAuthor() {
        return "CoreMC";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(final OfflinePlayer player, final @NotNull String params) {
        final PlaceholderHook.PlaceholderFunction fn = registry.get(params.toLowerCase(java.util.Locale.ROOT));
        if (fn == null) {
            return null;
        }
        return fn.apply(player);
    }
}
