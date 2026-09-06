package net.coremc.chatcolor.manager;

import net.coremc.foundation.CoreFoundation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the colour/gradient catalogue from config and persists selections.
 *
 * <p>Selections are cached per UUID and written asynchronously to SQLite.</p>
 */
public final class ColourManager {

    private final JavaPlugin plugin;
    private final List<ColourDef> catalogue = new ArrayList<>();
    private final java.util.Map<UUID, String> cache = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> boldCache = ConcurrentHashMap.newKeySet();
    private Connection conn;

    public ColourManager(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        loadCatalogue();
        final File f = new File(plugin.getDataFolder(), "chatcolours.db");
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath());
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS selections (uuid TEXT PRIMARY KEY, colour TEXT NOT NULL DEFAULT '', bold INTEGER NOT NULL DEFAULT 0)");
            primeCache();
            return true;
        } catch (final SQLException e) {
            plugin.getLogger().severe("SQLite init failed: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void loadCatalogue() {
        final ConfigurationSection root = plugin.getConfig().getConfigurationSection("colours");
        if (root == null) {
            return;
        }
        for (final String key : root.getKeys(false)) {
            final ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            final String type = s.getString("type", "solid").toUpperCase();
            final ColourDef.Kind kind = "GRADIENT".equals(type) ? ColourDef.Kind.GRADIENT : ColourDef.Kind.SOLID;
            final org.bukkit.Material mat;
            final String matName = s.getString("material", "");
            if (matName != null && !matName.isBlank()) {
                mat = safeMaterial(matName);
            } else {
                mat = kind == ColourDef.Kind.GRADIENT ? org.bukkit.Material.WHITE_CONCRETE : org.bukkit.Material.WHITE_DYE;
            }
            catalogue.add(new ColourDef(
                    key,
                    kind,
                    s.getString("display", key),
                    s.getString("value", ""),
                    s.getString("permission", ""),
                    s.getString("required", ""),
                    s.getInt("slot", catalogue.size()),
                    mat,
                    s.getBoolean("bold", false)));
        }
    }

    private void primeCache() {
        try (final PreparedStatement ps = conn.prepareStatement("SELECT uuid, colour, bold FROM selections")) {
            final ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                cache.put(UUID.fromString(rs.getString("uuid")), rs.getString("colour"));
                if (rs.getInt("bold") != 0) {
                    boldCache.add(UUID.fromString(rs.getString("uuid")));
                }
            }
        } catch (final SQLException e) {
            plugin.getLogger().warning("Failed to prime cache: " + e.getMessage());
        }
    }

    public List<ColourDef> catalogue() {
        return catalogue;
    }

    public ColourDef byId(final String id) {
        for (final ColourDef c : catalogue) {
            if (c.id().equalsIgnoreCase(id)) {
                return c;
            }
        }
        return null;
    }

    public boolean canUse(final Player player, final ColourDef def) {
        if (def.permission() == null || def.permission().isEmpty()) {
            return true;
        }
        return player.hasPermission(def.permission())
                || player.hasPermission("core.chatcolor.*");
    }

    private static org.bukkit.Material safeMaterial(final String name) {
        try {
            return org.bukkit.Material.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return org.bukkit.Material.PAPER;
        }
    }

    public String getSelected(final UUID uuid) {
        return cache.getOrDefault(uuid, plugin.getConfig().getString("default-colour", ""));
    }

    public void setSelected(final UUID uuid, final String id) {
        cache.put(uuid, id);
        final String sql = "INSERT INTO selections(uuid, colour) VALUES(?, ?) "
                + "ON CONFLICT(uuid) DO UPDATE SET colour = excluded.colour";
        final UUID u = uuid;
        final String sel = id;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (final PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, u.toString());
                ps.setString(2, sel);
                ps.executeUpdate();
            } catch (final SQLException e) {
                plugin.getLogger().warning("Failed to save selection: " + e.getMessage());
            }
        });
    }

    /** Whether the player has the global bold toggle enabled. */
    public boolean boldOn(final UUID uuid) {
        return boldCache.contains(uuid);
    }

    /** Enable/disable the global bold toggle for a player (persisted). */
    public void setBold(final UUID uuid, final boolean on) {
        if (on) {
            boldCache.add(uuid);
        } else {
            boldCache.remove(uuid);
        }
        final UUID u = uuid;
        final boolean state = on;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (final PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO selections(uuid, bold) VALUES(?, ?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET bold = excluded.bold")) {
                ps.setString(1, u.toString());
                ps.setBoolean(2, state);
                ps.executeUpdate();
            } catch (final SQLException e) {
                plugin.getLogger().warning("Failed to save bold state: " + e.getMessage());
            }
        });
    }

    /** MiniMessage tag that wraps a raw message in the player's colour. */
    public String format(final UUID uuid, final String message) {
        final String id = getSelected(uuid);
        if (id == null || id.isEmpty()) {
            return boldOn(uuid) ? "<bold>" + message + "</bold>" : message;
        }
        final ColourDef def = byId(id);
        if (def == null) {
            return boldOn(uuid) ? "<bold>" + message + "</bold>" : message;
        }
        final String coloured = def.value() + message + "</"
                + (def.kind() == ColourDef.Kind.GRADIENT ? "gradient" : "color") + ">";
        return def.bold() || boldOn(uuid) ? "<bold>" + coloured + "</bold>" : coloured;
    }

    /** Format with an explicit colour id (used by the API). */
    public String formatWith(final String id, final String message) {
        final ColourDef def = byId(id);
        if (def == null) {
            return message;
        }
        return def.value() + message + "</"
                + (def.kind() == ColourDef.Kind.GRADIENT ? "gradient" : "color") + ">";
    }

    public void shutdown() {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (final SQLException ignored) {
        }
    }
}
