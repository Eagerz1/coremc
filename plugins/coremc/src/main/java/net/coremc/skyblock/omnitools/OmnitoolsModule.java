package net.coremc.skyblock.omnitools;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.omnitools.ability.AbilityManager;
import net.coremc.skyblock.omnitools.command.ToolsCommand;
import net.coremc.skyblock.omnitools.gui.OmniMenu;
import net.coremc.skyblock.omnitools.listener.OmniEffectListener;
import net.coremc.skyblock.omnitools.listener.ToolListener;
import net.coremc.skyblock.omnitools.tool.RoleCurrencyManager;
import net.coremc.skyblock.omnitools.tool.ToolManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Omnitool + role-currency module. */
public final class OmnitoolsModule {

    private final JavaPlugin plugin;
    private OmniConfig omni;
    private RoleCurrencyManager currency;
    private ToolManager tools;
    private AbilityManager abilities;
    private OmniMenu menu;

    public OmnitoolsModule(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        CoreFoundation.getInstance().debug("OmnitoolsModule enabling");
        this.omni = new OmniConfig(plugin);
        this.omni.load();
        this.currency = new RoleCurrencyManager(plugin);
        this.tools = new ToolManager(plugin, currency, omni);
        this.abilities = new AbilityManager(plugin, tools, omni);
        this.abilities.reload();
        this.menu = new OmniMenu(tools, abilities, omni);

        plugin.getCommand("tools").setExecutor(new ToolsCommand(plugin, menu));
        plugin.getCommand("tools").setTabCompleter(new ToolsCommand(plugin, menu));

        plugin.getServer().getPluginManager().registerEvents(new ToolListener(tools, menu, omni), plugin);
        plugin.getServer().getPluginManager().registerEvents(new OmniEffectListener(tools, abilities), plugin);
        plugin.getLogger().info("OmnitoolsModule enabled.");
    }

    public void reload() {
        if (omni != null) omni.reload();
        if (tools != null) tools.reloadPerks();
        if (abilities != null) abilities.reload();
    }

    public void shutdown() {
        if (tools != null) tools.saveAll();
        if (currency != null) currency.saveAll();
    }

    public IslandApi islandApi() {
        return net.coremc.coremc.CoreMC.getInstance().islands().api();
    }

    public ToolManager tools() { return tools; }
    public RoleCurrencyManager currency() { return currency; }
    public AbilityManager abilities() { return abilities; }
    public OmniMenu menu() { return menu; }
    public OmniConfig config() { return omni; }
}
