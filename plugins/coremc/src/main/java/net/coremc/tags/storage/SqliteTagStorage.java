package net.coremc.tags.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** SQLite implementation of {@link TagStorage}. All I/O runs on the async thread. */
public final class SqliteTagStorage implements TagStorage {

    private final JavaPlugin plugin;
    private Connection conn;

    public SqliteTagStorage(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean init() {
        final File f = new File(plugin.getDataFolder(), "tags.db");
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath());
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS tag_ownership ("
                            + "uuid TEXT NOT NULL, tag TEXT NOT NULL, source TEXT, unlocked BIGINT, "
                            + "PRIMARY KEY(uuid, tag))");
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS tag_active (uuid TEXT PRIMARY KEY, tag TEXT)");
            return true;
        } catch (final SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Tag DB init failed", e);
            return false;
        }
    }

    @Override
    public void shutdown() {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (final SQLException ignored) {
        }
    }

    private void async(final Runnable r) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, r);
    }

    @Override
    public PlayerTags load(final UUID uuid) {
        final Set<String> owned = new HashSet<>();
        String active = null;
        try (final PreparedStatement ps = conn.prepareStatement("SELECT tag FROM tag_ownership WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            final ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                owned.add(rs.getString("tag"));
            }
        } catch (final SQLException e) {
            plugin.getLogger().warning("load owned failed: " + e.getMessage());
        }
        try (final PreparedStatement ps = conn.prepareStatement("SELECT tag FROM tag_active WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            final ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                active = rs.getString("tag");
                if (!owned.contains(active)) {
                    active = null; // defensive: active must be owned
                }
            }
        } catch (final SQLException e) {
            plugin.getLogger().warning("load active failed: " + e.getMessage());
        }
        return new PlayerTags(owned, active);
    }

    @Override
    public void unlock(final UUID uuid, final String tagId, final String source, final long timestamp) {
        async(() -> {
            try (final PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO tag_ownership(uuid, tag, source, unlocked) VALUES(?, ?, ?, ?) "
                            + "ON CONFLICT(uuid, tag) DO NOTHING")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, tagId);
                ps.setString(3, source);
                ps.setLong(4, timestamp);
                ps.executeUpdate();
            } catch (final SQLException e) {
                plugin.getLogger().warning("unlock failed: " + e.getMessage());
            }
        });
    }

    @Override
    public void remove(final UUID uuid, final String tagId) {
        async(() -> {
            try (final PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM tag_ownership WHERE uuid = ? AND tag = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, tagId);
                ps.executeUpdate();
            } catch (final SQLException e) {
                plugin.getLogger().warning("remove failed: " + e.getMessage());
            }
            // clear active if it was the removed tag
            try (final PreparedStatement ps = conn.prepareStatement(
                    "UPDATE tag_active SET tag = NULL WHERE uuid = ? AND tag = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, tagId);
                ps.executeUpdate();
            } catch (final SQLException e) {
                plugin.getLogger().warning("clear active failed: " + e.getMessage());
            }
        });
    }

    @Override
    public void setActive(final UUID uuid, final String tagId) {
        async(() -> {
            try (final PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO tag_active(uuid, tag) VALUES(?, ?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET tag = excluded.tag")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, tagId);
                ps.executeUpdate();
            } catch (final SQLException e) {
                plugin.getLogger().warning("setActive failed: " + e.getMessage());
            }
        });
    }
}
