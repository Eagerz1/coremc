package net.coremc.skyblock.core;

import net.coremc.foundation.CoreFoundation;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.api.IslandApiImpl;
import net.coremc.skyblock.core.api.ProgressionProvider;
import net.coremc.skyblock.core.command.IslandCommand;
import net.coremc.skyblock.core.island.IslandManager;
import net.coremc.skyblock.core.listener.IslandBorderListener;
import net.coremc.skyblock.core.listener.IslandListener;
import net.coremc.skyblock.core.storage.Database;
import net.coremc.skyblock.core.storage.IslandStorage;
import net.coremc.skyblock.core.storage.SqlStorage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

import java.util.logging.Level;

/**
 * Core SkyBlock island module for CoreMC (consolidated plugin).
 */
public final class IslandModule {

    private final JavaPlugin plugin;
    private IslandStorage storage;
    private IslandManager islandManager;
    private IslandApi api;
    private ProgressionProvider progressionProvider;

    public IslandModule(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        CoreFoundation.getInstance().debug("IslandModule enabling");

        final Database db = new Database("jdbc:sqlite:" + new File(plugin.getDataFolder(), "islands.db").getAbsolutePath(), "", "", plugin.getLogger());
        if (!db.init()) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialise island storage - disabling module.");
            Bukkit.getPluginManager().disablePlugin(plugin);
            return;
        }
        this.storage = new SqlStorage(db, plugin);
        this.storage.init();

        this.islandManager = new IslandManager(plugin, storage);
        this.api = new IslandApiImpl(plugin, storage, islandManager);

        exportSchematic("plains.schem");
        exportSchematic("desert.schem");
        exportSchematic("mushroom.schem");

        plugin.getCommand("is").setExecutor(new IslandCommand(plugin, api, islandManager));
        plugin.getCommand("is").setTabCompleter(new IslandCommand(plugin, api, islandManager));
        Bukkit.getPluginManager().registerEvents(new IslandListener(plugin, api), plugin);
        final IslandBorderListener border = new IslandBorderListener();
        border.register();
        Bukkit.getPluginManager().registerEvents(border, plugin);

        plugin.getLogger().info("IslandModule enabled.");
    }

    public void shutdown() {
        if (storage != null) {
            storage.close();
        }
    }

    /** Copy a bundled schematic from the jar's resources/ folder into the data folder. */
    private void exportSchematic(final String name) {
        final java.io.File out = new java.io.File(plugin.getDataFolder(),
                "schematics" + java.io.File.separator + name);
        if (out.exists()) return;
        try {
            final java.io.InputStream res = plugin.getResource("schematics/" + name);
            if (res == null) {
                plugin.getLogger().warning("Bundled schematic missing: " + name);
                return;
            }
            out.getParentFile().mkdirs();
            try (res) {
                java.nio.file.Files.copy(res, out.toPath());
            }
            plugin.getLogger().info("Exported schematic " + name);
        } catch (final java.io.IOException e) {
            plugin.getLogger().warning("Could not export schematic " + name + ": " + e.getMessage());
        }
    }

    public IslandStorage storage() {
        return storage;
    }

    public IslandManager islandManager() {
        return islandManager;
    }

    public IslandApi api() {
        return api;
    }

    public void setProgressionProvider(final ProgressionProvider provider) {
        this.progressionProvider = provider;
    }

    public ProgressionProvider progressionProvider() {
        return progressionProvider;
    }
}
