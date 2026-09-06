package net.coremc.foundation;

import net.coremc.foundation.gui.GuiListener;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.messages.Messages;
import net.coremc.foundation.util.CooldownManager;
import net.coremc.foundation.util.Version;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared foundation utilities for CoreMC.
 *
 * <p>When running inside the consolidated CoreMC plugin, an instance is created
 * by {@code CoreMC} and exposed through {@link #getInstance()}. Other plugins
 * (and CoreMC subsystems) depend on this single instance for messages, GUI
 * routing, formatting, cooldowns, version info and debug logging.</p>
 */
public final class CoreFoundation {

    private static CoreFoundation instance;

    private final JavaPlugin plugin;
    private Messages messages;
    private CooldownManager cooldowns;
    private Version version;
    private boolean debug;

    private final Map<Inventory, InventoryGui> openGuis = new HashMap<>();

    public CoreFoundation(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.messages = new Messages(plugin);
        this.cooldowns = new CooldownManager();
        this.version = new Version(plugin);
        this.debug = plugin.getConfig().getBoolean("debug.enabled", false);
        instance = this;
        Bukkit.getPluginManager().registerEvents(new GuiListener(plugin), plugin);
        PlaceholderHook.register(plugin);
    }

    public static CoreFoundation getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CoreFoundation is not enabled.");
        }
        return instance;
    }

    /** Reloads {@code config.yml} and re-reads cached settings. */
    public void reloadConfiguration() {
        plugin.reloadConfig();
        this.messages.reload();
        this.debug = plugin.getConfig().getBoolean("debug.enabled", false);
        plugin.getLogger().info("CoreFoundation configuration reloaded.");
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    /** Delegate to the backing plugin's config. */
    public org.bukkit.configuration.file.FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public java.util.logging.Logger getLogger() {
        return plugin.getLogger();
    }

    public org.bukkit.plugin.PluginDescriptionFile getDescription() {
        return plugin.getDescription();
    }

    public Messages messages() {
        return messages;
    }

    public CooldownManager cooldowns() {
        return cooldowns;
    }

    public Version version() {
        return version;
    }

    public boolean isDebug() {
        return debug;
    }

    /** Debug log — only printed when debug mode is enabled. */
    public void debug(String message) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    // ---- GUI registry (used by InventoryGui + GuiListener) ----

    /** Register an open GUI so the click listener can route events. */
    public void registerGui(InventoryGui gui) {
        openGuis.put(gui.getInventory(), gui);
    }

    /** Unregister a GUI (called on close). */
    public void unregisterGui(InventoryGui gui) {
        openGuis.remove(gui.getInventory());
    }

    /** Look up the GUI bound to an inventory. */
    public InventoryGui getGui(Inventory inventory) {
        return openGuis.get(inventory);
    }
}
