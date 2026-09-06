package net.coremc.skyblock.playerdata;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Wires the persistent player-data system into CoreMC's lifecycle.
 *
 * <p>Exposes the {@link PlayerDataManager} as the single source of truth and
 * registers the join/quit loader, autosave, daily backup and the
 * {@code /playerdata} admin command. Configuration (config.yml / lootboxes /
 * menus) is never read or written here for ownership data — that separation is
 * the whole point of this module.</p>
 */
public final class PlayerDataModule {

    public static final String ADMIN_PERM = "core.playerdata";

    private final JavaPlugin plugin;
    private PlayerDataManager manager;

    public PlayerDataModule(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        this.manager = new PlayerDataManager(plugin);
        manager.init();

        Bukkit.getPluginManager().registerEvents(new PlayerDataListener(manager), plugin);

        plugin.getCommand("playerdata").setExecutor(new PlayerDataCommand(plugin, manager));
        plugin.getCommand("playerdata").setTabCompleter(new PlayerDataCommand(plugin, manager));

        CoreMC.getInstance().setPlayerDataManager(manager);

        plugin.getLogger().info("[playerdata] PlayerDataModule enabled.");
    }

    public void shutdown() {
        if (manager != null) manager.shutdown();
    }

    public PlayerDataManager manager() { return manager; }
}
