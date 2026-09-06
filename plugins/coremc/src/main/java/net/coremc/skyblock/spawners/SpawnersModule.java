package net.coremc.skyblock.spawners;
import net.coremc.coremc.CoreMC;

import net.coremc.foundation.CoreFoundation;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.spawners.command.SpawnersCommand;
import net.coremc.skyblock.spawners.listener.SpawnerListener;
import net.coremc.skyblock.spawners.listener.SpawnerPickupListener;
import net.coremc.skyblock.spawners.spawner.SpawnerManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Mob spawner system module. */
public final class SpawnersModule {

    private final JavaPlugin plugin;
    private SpawnerManager spawners;

    public SpawnersModule(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        CoreFoundation.getInstance().debug("SpawnersModule enabling");
        this.spawners = new SpawnerManager(plugin);
        plugin.getCommand("spawners").setExecutor(new SpawnersCommand(plugin, spawners));
        plugin.getCommand("spawners").setTabCompleter(new SpawnersCommand(plugin, spawners));
        plugin.getServer().getPluginManager().registerEvents(new SpawnerListener(plugin, spawners), plugin);
        plugin.getServer().getPluginManager().registerEvents(new SpawnerPickupListener(plugin, spawners), plugin);
        plugin.getLogger().info("SpawnersModule enabled.");
    }

    public void shutdown() {
        if (spawners != null) spawners.shutdown();
    }

    public IslandApi islandApi() {
        return net.coremc.coremc.CoreMC.getInstance().islands().api();
    }

    public SpawnerManager spawners() {
        return spawners;
    }
}
