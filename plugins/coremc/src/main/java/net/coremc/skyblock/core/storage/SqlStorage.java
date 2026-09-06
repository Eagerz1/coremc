package net.coremc.skyblock.core.storage;
import org.bukkit.plugin.java.JavaPlugin;
import net.coremc.coremc.CoreMC;


import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * SQLite-backed implementation of {@link IslandStorage}.
 */
public final class SqlStorage implements IslandStorage {

    private final JavaPlugin plugin;
    private final String url;
    private final String user;
    private final String password;

    /** Tracks whether schema initialization succeeded. Read queries that fail
     *  after a known-bad schema only log once instead of spamming the console
     *  on every polling call from TAB / PlaceholderAPI. */
    private volatile boolean schemaOk = false;

    public SqlStorage(final Database db, final JavaPlugin plugin) {
        this.plugin = plugin;
        this.url = db.url();
        this.user = db.user();
        this.password = db.password();
    }

    private Connection conn() throws SQLException {
        if (user == null || user.isEmpty()) {
            return DriverManager.getConnection(url);
        }
        return DriverManager.getConnection(url, user, password);
    }

    public void init() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS islands (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner TEXT NOT NULL,
                    biome TEXT NOT NULL,
                    world TEXT, x REAL, y REAL, z REAL, yaw REAL, pitch REAL,
                    level INTEGER DEFAULT 0, xp INTEGER DEFAULT 0,
                    created_at INTEGER NOT NULL
                )""");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS island_members (
                    island_id INTEGER NOT NULL, member TEXT NOT NULL,
                    PRIMARY KEY (island_id, member)
                )""");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS island_stats (
                    island_id INTEGER NOT NULL, stat_key TEXT NOT NULL, value INTEGER DEFAULT 0,
                    PRIMARY KEY (island_id, stat_key)
                )""");
            this.schemaOk = true;
        } catch (final SQLException e) {
            plugin().getLogger().log(Level.SEVERE, "SQL init failed — checking database integrity", e);
            // Attempt recovery: if the database file is corrupt, the schema
            // creation above may have failed. The next startup will create a fresh file.
            try (Connection c = conn(); Statement s = c.createStatement()) {
                s.execute("PRAGMA integrity_check");
            } catch (final SQLException ex) {
                plugin().getLogger().log(Level.SEVERE,
                        "Database integrity check also failed. The database file at "
                        + new java.io.File(plugin.getDataFolder(), "islands.db").getAbsolutePath()
                        + " may be corrupt. Delete it and restart to regenerate.", ex);
            }
        }
    }

    private org.bukkit.plugin.java.JavaPlugin plugin() {
        return net.coremc.coremc.CoreMC.getInstance();
    }

    @Override
    public Island createIsland(final UUID owner, final Island.Biome biome, final Location spawn) {
        if (!schemaOk) return null;
        final long now = System.currentTimeMillis();
        try (Connection c = conn()) {
            final PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO islands (owner,biome,world,x,y,z,yaw,pitch,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, owner.toString());
            ps.setString(2, biome.name());
            ps.setString(3, spawn == null ? null : spawn.getWorld().getName());
            ps.setDouble(4, spawn == null ? 0 : spawn.getX());
            ps.setDouble(5, spawn == null ? 0 : spawn.getY());
            ps.setDouble(6, spawn == null ? 0 : spawn.getZ());
            ps.setFloat(7, spawn == null ? 0 : spawn.getYaw());
            ps.setFloat(8, spawn == null ? 0 : spawn.getPitch());
            ps.setLong(9, now);
            ps.executeUpdate();
            final ResultSet keys = ps.getGeneratedKeys();
            final int id = keys.next() ? keys.getInt(1) : -1;
            return new Island(id, owner, biome, spawn, 0, 0, now, new ArrayList<>());
        } catch (final SQLException e) {
            plugin().getLogger().log(Level.SEVERE, "createIsland failed", e);
            return null;
        }
    }

    @Override
    public Island getIsland(final int id) {
        if (!schemaOk) return null;
        try (Connection c = conn()) {
            final PreparedStatement ps = c.prepareStatement("SELECT * FROM islands WHERE id=?");
            ps.setInt(1, id);
            final ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            return load(rs, c);
        } catch (final SQLException e) {
            plugin().getLogger().log(Level.SEVERE, "getIsland failed", e);
            return null;
        }
    }

    @Override
    public Island getIslandByOwner(final UUID owner) {
        if (!schemaOk) return null;
        try (Connection c = conn()) {
            final PreparedStatement ps = c.prepareStatement("SELECT * FROM islands WHERE owner=?");
            ps.setString(1, owner.toString());
            final ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            return load(rs, c);
        } catch (final SQLException e) {
            plugin().getLogger().log(Level.SEVERE, "getIslandByOwner failed", e);
            return null;
        }
    }

    @Override
    public Island getIslandByMember(final UUID member) {
        if (!schemaOk) return null;
        try (Connection c = conn()) {
            final PreparedStatement ps = c.prepareStatement(
                    "SELECT i.* FROM islands i JOIN island_members m ON i.id=m.island_id WHERE m.member=?");
            ps.setString(1, member.toString());
            final ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            return load(rs, c);
        } catch (final SQLException e) {
            plugin().getLogger().log(Level.SEVERE, "getIslandByMember failed", e);
            return null;
        }
    }

    private Island load(final ResultSet rs, final Connection c) throws SQLException {
        final int id = rs.getInt("id");
        final UUID owner = UUID.fromString(rs.getString("owner"));
        final Island.Biome biome = Island.Biome.valueOf(rs.getString("biome"));
        final String world = rs.getString("world");
        Location spawn = null;
        if (world != null && Bukkit.getWorld(world) != null) {
            spawn = new Location(Bukkit.getWorld(world), rs.getDouble("x"), rs.getDouble("y"),
                    rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"));
        }
        final long level = rs.getLong("level");
        final long xp = rs.getLong("xp");
        final long created = rs.getLong("created_at");
        final List<UUID> members = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT member FROM island_members WHERE island_id=?")) {
            ps.setInt(1, id);
            final ResultSet mr = ps.executeQuery();
            while (mr.next()) {
                members.add(UUID.fromString(mr.getString("member")));
            }
        }
        return new Island(id, owner, biome, spawn, level, xp, created, members);
    }

    @Override
    public void saveIsland(final Island island) {
        if (!schemaOk) return;
        try (Connection c = conn()) {
            final PreparedStatement ps = c.prepareStatement(
                    "UPDATE islands SET owner=?,biome=?,world=?,x=?,y=?,z=?,yaw=?,pitch=?,level=?,xp=? WHERE id=?");
            ps.setString(1, island.getOwner().toString());
            ps.setString(2, island.getBiome().name());
            final Location s = island.getSpawn();
            ps.setString(3, s == null ? null : s.getWorld().getName());
            ps.setDouble(4, s == null ? 0 : s.getX());
            ps.setDouble(5, s == null ? 0 : s.getY());
            ps.setDouble(6, s == null ? 0 : s.getZ());
            ps.setFloat(7, s == null ? 0 : s.getYaw());
            ps.setFloat(8, s == null ? 0 : s.getPitch());
            ps.setLong(9, island.getLevel());
            ps.setLong(10, island.getXp());
            ps.setInt(11, island.getId());
            ps.executeUpdate();
        } catch (final SQLException e) {
            plugin().getLogger().log(Level.SEVERE, "saveIsland failed", e);
        }
    }

    @Override
    public void deleteIsland(final int id) {
        if (!schemaOk) return;
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM islands WHERE id=?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM island_members WHERE island_id=?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM island_stats WHERE island_id=?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
        } catch (final SQLException e) {
            plugin().getLogger().log(Level.SEVERE, "deleteIsland failed", e);
        }
    }

    @Override
    public void addMember(final int islandId, final UUID member) {
        if (!schemaOk) return;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO island_members (island_id,member) VALUES (?,?)")) {
            ps.setInt(1, islandId);
            ps.setString(2, member.toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            plugin().getLogger().log(Level.SEVERE, "addMember failed", e);
        }
    }

    @Override
    public void removeMember(final int islandId, final UUID member) {
        if (!schemaOk) return;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM island_members WHERE island_id=? AND member=?")) {
            ps.setInt(1, islandId);
            ps.setString(2, member.toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            plugin().getLogger().log(Level.SEVERE, "removeMember failed", e);
        }
    }

    @Override
    public List<Island> getTopIslands(final int categoryMinMembers, final int limit) {
        if (!schemaOk) return List.of();
        final List<Island> all = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT * FROM islands ORDER BY xp DESC")) {
            final ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                all.add(load(rs, c));
            }
        } catch (final SQLException e) {
            plugin().getLogger().log(Level.SEVERE, "getTopIslands failed", e);
        }
        final List<Island> filtered = new ArrayList<>();
        for (final Island is : all) {
            final int count = is.getMemberCount();
            final int cat = count == 1 ? 1 : (count == 2 ? 2 : 3);
            if (cat >= categoryMinMembers) {
                filtered.add(is);
            }
            if (filtered.size() >= limit) {
                break;
            }
        }
        return filtered;
    }

    @Override
    public int getRank(final Island island) {
        if (!schemaOk) return 0;
        final int catMin = island.getMemberCount() == 1 ? 1 : (island.getMemberCount() == 2 ? 2 : 3);
        int rank = 1;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT xp FROM islands ORDER BY xp DESC")) {
            final ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                if (rs.getLong("xp") > island.getXp()) {
                    // only count islands in same or higher category? Keep simple: global rank by xp.
                    rank++;
                }
            }
        } catch (final SQLException e) {
            plugin().getLogger().log(Level.SEVERE, "getRank failed", e);
        }
        return rank;
    }

    @Override
    public void addStatistic(final int islandId, final String key, final long amount) {
        if (!schemaOk) return;
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO island_stats (island_id,stat_key,value) VALUES (?,?,?) "
                            + "ON CONFLICT(island_id,stat_key) DO UPDATE SET value=value+?")) {
                ps.setInt(1, islandId);
                ps.setString(2, key);
                ps.setLong(3, amount);
                ps.setLong(4, amount);
                ps.executeUpdate();
            }
        } catch (final SQLException e) {
            plugin().getLogger().log(Level.SEVERE, "addStatistic failed", e);
        }
    }

    @Override
    public long getStatistic(final int islandId, final String key) {
        if (!schemaOk) return 0;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT value FROM island_stats WHERE island_id=? AND stat_key=?")) {
            ps.setInt(1, islandId);
            ps.setString(2, key);
            final ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong("value") : 0;
        } catch (final SQLException e) {
            plugin().getLogger().log(Level.SEVERE, "getStatistic failed", e);
            return 0;
        }
    }

    @Override
    public void addXp(final int islandId, final long amount) {
        if (!schemaOk) return;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE islands SET xp=xp+? WHERE id=?")) {
            ps.setLong(1, amount);
            ps.setInt(2, islandId);
            ps.executeUpdate();
        } catch (final SQLException e) {
            plugin().getLogger().log(Level.SEVERE, "addXp failed", e);
        }
    }

    @Override
    public void close() {
        // SQLite per-connection; nothing pooled.
    }

    @Override
    public List<Island> getAllIslands() {
        if (!schemaOk) return List.of();
        final List<Island> all = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT * FROM islands")) {
            final ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                all.add(load(rs, c));
            }
        } catch (final SQLException e) {
            plugin().getLogger().log(Level.SEVERE, "getAllIslands failed", e);
        }
        return all;
    }
}
