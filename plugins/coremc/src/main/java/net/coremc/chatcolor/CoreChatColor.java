package net.coremc.chatcolor;

import net.coremc.chatcolor.api.ChatColorApi;
import net.coremc.chatcolor.command.ChatColorCommand;
import net.coremc.chatcolor.listener.ChatListener;
import net.coremc.chatcolor.manager.ColourManager;
import net.coremc.foundation.CoreFoundation;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Player-selected chat colours and gradients for CoreMC.
 *
 * <p>Reads the colour/gradient catalogue from {@code config.yml}, persists each
 * player's selection by UUID, and applies the selection to chat without
 * touching player names, rank prefixes or click/hover events.</p>
 */
public final class CoreChatColor extends JavaPlugin {

    private static CoreChatColor instance;
    private ColourManager colours;
    private ChatColorApi api;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        CoreFoundation.getInstance().debug("CoreChatColor enabling");

        this.colours = new ColourManager(this);
        if (!colours.init()) {
            getLogger().log(Level.SEVERE, "Could not init chat-colour storage - disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.api = new ChatColorApi(this, colours);

        getCommand("chatcolor").setExecutor(new ChatColorCommand(this, colours));
        getCommand("chatcolor").setTabCompleter(new ChatColorCommand(this, colours));
        getServer().getPluginManager().registerEvents(new ChatListener(this, colours), this);

        getLogger().info("CoreChatColor v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (colours != null) {
            colours.shutdown();
        }
        instance = null;
        getLogger().info("CoreChatColor disabled.");
    }

    public static CoreChatColor getInstance() {
        return instance;
    }

    public ColourManager colours() {
        return colours;
    }

    public ChatColorApi api() {
        return api;
    }

    /** Open the CoreTags "Tags" store section from within /chatcolor (section 6). */
    public void openTagsSection(final org.bukkit.entity.Player p) {
        if (!getServer().getPluginManager().isPluginEnabled("core-tags")) {
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix() + " <red>Tags are unavailable.");
            return;
        }
        try {
            final org.bukkit.plugin.java.JavaPlugin tags =
                    (org.bukkit.plugin.java.JavaPlugin) getServer().getPluginManager().getPlugin("core-tags");
            tags.getClass().getMethod("api").invoke(tags); // ensure loaded
            // Open the store-style tag browser.
            final Class<?> gui = Class.forName("net.coremc.tags.gui.TagsGui");
            gui.getMethod("openStore", org.bukkit.entity.Player.class).invoke(null, p);
        } catch (final Throwable t) {
            getLogger().warning("Could not open tags section: " + t.getMessage());
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix() + " <red>Tags are unavailable.");
        }
    }
}
