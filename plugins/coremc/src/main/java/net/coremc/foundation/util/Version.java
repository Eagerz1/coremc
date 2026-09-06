package net.coremc.foundation.util;
import org.bukkit.plugin.java.JavaPlugin;


import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

/**
 * Version / environment information exposed to dependent plugins.
 */
public final class Version {

    private final JavaPlugin plugin;

    public Version(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public @NotNull String getPluginVersion() {
        return plugin.getDescription().getVersion();
    }

    public @NotNull String getServerVersion() {
        return Bukkit.getBukkitVersion();
    }

    public @NotNull String getMinecraftVersion() {
        final String nms = Bukkit.getServer().getClass().getPackage().getName();
        final int idx = nms.lastIndexOf('.');
        return idx >= 0 ? nms.substring(idx + 1) : nms;
    }

    public @NotNull String getFullVersion() {
        return "CoreFoundation " + getPluginVersion()
                + " (MC " + getMinecraftVersion() + ")";
    }

    /** True if the running server is Paper or a Paper fork. */
    public boolean isPaper() {
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }
}
