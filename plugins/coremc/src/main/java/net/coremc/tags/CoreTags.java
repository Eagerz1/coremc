package net.coremc.tags;

import net.coremc.foundation.CoreFoundation;
import net.coremc.tags.api.CoreTagsApi;
import net.coremc.tags.command.TagsCommand;
import net.coremc.tags.listener.PlayerDataListener;
import net.coremc.tags.listener.TagChatListener;
import net.coremc.tags.manager.TagManager;
import net.coremc.tags.storage.SqliteTagStorage;
import net.coremc.tags.storage.TagStorage;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Collectible player chat tags for CoreMC.
 *
 * <p>Tags are independent of rank prefixes (handled by the CoreMC rank system)
 * and chat colours (handled by core-chatcolor). CoreTags owns only the tag:
 * its display, gradient, ownership, selection and chat placement.</p>
 */
public final class CoreTags extends JavaPlugin {

    private static CoreTags instance;
    private TagManager manager;
    private CoreTagsApi api;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("tags.yml", false);

        final TagStorage storage = new SqliteTagStorage(this);
        if (!storage.init()) {
            getLogger().log(Level.SEVERE, "Could not init tag storage - disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.manager = new TagManager(this, storage);
        this.api = new CoreTagsApi(this, manager);

        getCommand("tags").setExecutor(new TagsCommand(this, manager, api));
        getCommand("tags").setTabCompleter(new TagsCommand(this, manager, api));
        getServer().getPluginManager().registerEvents(new TagChatListener(this, manager), this);
        getServer().getPluginManager().registerEvents(new PlayerDataListener(this, manager), this);

        // Expose the API for other CoreMC plugins (crate/store integration).
        getServer().getServicesManager().register(CoreTagsApi.class, api, this, org.bukkit.plugin.ServicePriority.Normal);

        getLogger().info("CoreTags v" + getDescription().getVersion() + " enabled (" + manager.catalogue().size() + " tags).");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
        instance = null;
        getLogger().info("CoreTags disabled.");
    }

    public static CoreTags getInstance() {
        return instance;
    }

    public TagManager manager() {
        return manager;
    }

    public CoreTagsApi api() {
        return api;
    }
}
