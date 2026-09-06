package net.coremc.foundation.util;
import net.coremc.foundation.CoreFoundation;


import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Small helpers for reading typed values out of configuration safely.
 */
public final class ConfigUtil {

    private ConfigUtil() {}

    public static @NotNull FileConfiguration config() {
        return CoreFoundation.getInstance().getConfig();
    }

    public static @Nullable ConfigurationSection section(final @NotNull String path) {
        return config().getConfigurationSection(path);
    }

    public static @NotNull String string(final @NotNull String path, final @NotNull String def) {
        return config().getString(path, def);
    }

    public static boolean bool(final @NotNull String path, final boolean def) {
        return config().getBoolean(path, def);
    }

    public static int integer(final @NotNull String path, final int def) {
        return config().getInt(path, def);
    }

    public static long longVal(final @NotNull String path, final long def) {
        return config().getLong(path, def);
    }

    public static double doubleVal(final @NotNull String path, final double def) {
        return config().getDouble(path, def);
    }
}
