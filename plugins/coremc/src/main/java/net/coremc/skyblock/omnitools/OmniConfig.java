package net.coremc.skyblock.omnitools;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * Loads the OmniTool's own configuration from {@code omnitools.yml} (shipped as a
 * plugin resource, extracted to the data folder on first run). Keeping the OmniTool
 * config in its own file makes it fully configurable and independent of the main
 * {@code config.yml} (which is shared with the rest of CoreMC).
 *
 * <p>Operator edits to {@code plugins/CoreMC/omnitools.yml} are preserved across
 * restarts (the file is only created if absent). Call {@link #reload()} on a plugin
 * reload to pick up changes.</p>
 */
public final class OmniConfig {

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration config;

    public OmniConfig(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "omnitools.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("omnitools.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration config() {
        return config;
    }

    public void reload() {
        load();
    }
}
