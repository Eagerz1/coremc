package net.coremc.coreban;

import net.coremc.coreban.command.BanCommand;
import net.coremc.coreban.command.HistoryCommand;
import net.coremc.coreban.command.KickCommand;
import net.coremc.coreban.command.MuteCommand;
import net.coremc.coreban.command.NoteCommand;
import net.coremc.coreban.command.NotesCommand;
import net.coremc.coreban.command.UnbanCommand;
import net.coremc.coreban.command.UnmuteCommand;
import net.coremc.coreban.command.WarnCommand;
import net.coremc.coreban.listener.ChatListener;
import net.coremc.coreban.storage.MySqlNoteStorage;
import net.coremc.coreban.storage.NoteStorage;
import net.coremc.coreban.storage.PunishmentStorage;
import net.coremc.coreban.storage.SqliteNoteStorage;
import net.coremc.foundation.CoreFoundation;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tiered punishment system module. */
public final class BanModule {

    private final JavaPlugin plugin;
    private PunishmentStorage storage;
    private NoteStorage notes;
    private final Map<UUID, net.coremc.coreban.model.Punishment> muted = new ConcurrentHashMap<>();

    public BanModule(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        CoreFoundation.getInstance().debug("BanModule enabling");
        loadStorage();
        plugin.getCommand("ban").setExecutor(new BanCommand(plugin, storage));
        plugin.getCommand("mute").setExecutor(new MuteCommand(plugin, storage));
        plugin.getCommand("warn").setExecutor(new WarnCommand(plugin, storage));
        plugin.getCommand("kick").setExecutor(new KickCommand(plugin, storage));
        plugin.getCommand("unban").setExecutor(new UnbanCommand(plugin, storage));
        plugin.getCommand("unmute").setExecutor(new UnmuteCommand(plugin, storage));
        plugin.getCommand("history").setExecutor(new HistoryCommand(plugin, storage));
        plugin.getCommand("note").setExecutor(new NoteCommand(plugin, notes()));
        plugin.getCommand("notes").setExecutor(new NotesCommand(plugin, notes()));
        plugin.getServer().getPluginManager().registerEvents(new ChatListener(plugin, storage), plugin);
        plugin.getLogger().info("BanModule enabled.");
    }

    private NoteStorage notes() {
        if (notes == null) {
            loadNoteStorage();
        }
        return notes;
    }

    public void shutdown() {
        if (storage != null) {
            storage.close();
        }
        if (notes != null) {
            notes.close();
        }
    }

    private void loadStorage() {
        final String type = plugin.getConfig().getString("storage.type", "sqlite");
        if ("mysql".equalsIgnoreCase(type)) {
            final var c = plugin.getConfig().getConfigurationSection("storage.mysql");
            storage = new net.coremc.coreban.storage.MySqlStorage(
                    c.getString("host"), c.getInt("port"), c.getString("database"),
                    c.getString("user"), c.getString("password"));
        } else {
            storage = new net.coremc.coreban.storage.SqliteStorage(plugin);
        }
        storage.init();
    }

    private void loadNoteStorage() {
        final String type = plugin.getConfig().getString("storage.type", "sqlite");
        if ("mysql".equalsIgnoreCase(type)) {
            final var c = plugin.getConfig().getConfigurationSection("storage.mysql");
            notes = new MySqlNoteStorage(
                    c.getString("host"), c.getInt("port"), c.getString("database"),
                    c.getString("user"), c.getString("password"));
        } else {
            notes = new SqliteNoteStorage(plugin);
        }
        notes.init();
    }

    public PunishmentStorage storage() {
        return storage;
    }

    public NoteStorage noteStorage() {
        return notes();
    }

    public Map<UUID, net.coremc.coreban.model.Punishment> muted() {
        return muted;
    }
}
