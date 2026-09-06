package net.coremc.skyblock.gens;
import net.coremc.coremc.CoreMC;

import net.coremc.foundation.CoreFoundation;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.gens.command.GensCommand;
import net.coremc.skyblock.gens.generator.GeneratorManager;
import net.coremc.skyblock.gens.listener.GenListener;
import net.coremc.skyblock.gens.listener.GenPickupListener;
import org.bukkit.plugin.java.JavaPlugin;

/** Generator system module. */
public final class GensModule {

    private final JavaPlugin plugin;
    private GeneratorManager generators;

    public GensModule(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        CoreFoundation.getInstance().debug("GensModule enabling");
        this.generators = new GeneratorManager(plugin);
        plugin.getCommand("gens").setExecutor(new GensCommand(plugin, generators));
        plugin.getCommand("gens").setTabCompleter(new GensCommand(plugin, generators));
        plugin.getServer().getPluginManager().registerEvents(new GenListener(plugin, generators), plugin);
        plugin.getServer().getPluginManager().registerEvents(new GenPickupListener(plugin, generators), plugin);
        plugin.getLogger().info("GensModule enabled.");
    }

    public void shutdown() {
        if (generators != null) generators.shutdown();
    }

    public IslandApi islandApi() {
        return net.coremc.coremc.CoreMC.getInstance().islands().api();
    }

    public GeneratorManager generators() {
        return generators;
    }
}
