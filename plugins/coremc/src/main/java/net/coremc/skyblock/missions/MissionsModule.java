package net.coremc.skyblock.missions;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Missions module: loads configurable missions, tracks per-player progress from
 * existing SkyBlock activities, and pays rewards through the existing CoreMC
 * systems (money, Sky Tokens, Credits, keys, items, island XP).
 */
public final class MissionsModule {

    private final JavaPlugin plugin;
    private MissionManager manager;

    public MissionsModule(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        this.manager = new MissionManager(plugin);
        manager.init();

        plugin.getCommand("missions").setExecutor(new MissionsCommand(manager));
        plugin.getServer().getPluginManager().registerEvents(new MissionsListener(manager), plugin);
        plugin.getLogger().info("MissionsModule enabled.");
    }

    public void shutdown() {
        if (manager != null) manager.shutdown();
    }

    public MissionManager manager() {
        return manager;
    }
}
