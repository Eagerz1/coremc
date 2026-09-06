package net.coremc.skyblock.companion;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Companion progression module. Owns the {@link CompanionManager}, registers the
 * /companion command + GUI, and the XP-awarding activity listener. Persists to
 * companions.yml via the manager. Uses only existing CoreMC systems (roles, drops,
 * money, Sky Tokens) — no new currency is introduced.
 */
public final class CompanionModule {

    private final JavaPlugin plugin;
    private CompanionManager manager;
    private CompanionCommand command;

    public CompanionModule(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        this.manager = new CompanionManager(plugin);
        this.command = new CompanionCommand(plugin, manager);

        final org.bukkit.command.PluginCommand pc = plugin.getCommand("companion");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }

        Bukkit.getPluginManager().registerEvents(new CompanionListener(plugin, manager), plugin);

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, manager::save, 6000L, 6000L);
        plugin.getLogger().info("[companions] Companion module enabled.");
    }

    public void shutdown() {
        if (manager != null) manager.save();
    }

    public CompanionManager manager() { return manager; }
    public CompanionGui gui() { return command.gui(); }
}
