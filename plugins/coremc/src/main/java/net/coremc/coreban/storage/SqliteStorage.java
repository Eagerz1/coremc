package net.coremc.coreban.storage;


import net.coremc.coreban.model.Punishment;
import net.coremc.coreban.model.PunishmentType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQLite-backed punishment storage.
 */
public final class SqliteStorage implements PunishmentStorage {

    private final JavaPlugin plugin;
    private final String path;

    public SqliteStorage(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.path = new File(plugin.getDataFolder(), "punishments.db").getAbsolutePath();
    }

    private Connection conn() throws java.sql.SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + path);
    }

    @Override
    public void init() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            Class.forName("org.sqlite.JDBC");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS punishments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    target TEXT NOT NULL,
                    type TEXT NOT NULL,
                    tier INTEGER NOT NULL,
                    offence INTEGER NOT NULL,
                    duration INTEGER NOT NULL,
                    reason TEXT,
                    staff TEXT,
                    issued_at INTEGER NOT NULL,
                    appealed INTEGER DEFAULT 0,
                    expired_at INTEGER DEFAULT 0
                )""");
        } catch (final Exception e) {
            plugin.getLogger().severe("CoreBan SQLite init failed: " + e.getMessage());
        }
    }

    @Override
    public Punishment insert(final Punishment p) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO punishments (target,type,tier,offence,duration,reason,staff,issued_at,appealed,expired_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.target().toString());
            ps.setString(2, p.type().name());
            ps.setInt(3, p.tier());
            ps.setInt(4, p.offenceNumber());
            ps.setLong(5, p.durationSeconds());
            ps.setString(6, p.reason());
            ps.setString(7, p.staff());
            ps.setLong(8, p.issuedAt());
            ps.setInt(9, p.appealed() ? 1 : 0);
            ps.setLong(10, p.expiredAt());
            ps.executeUpdate();
            final ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                final Punishment copy = new Punishment(keys.getInt(1), p.target(), p.type(), p.tier(),
                        p.offenceNumber(), p.durationSeconds(), p.reason(), p.staff(),
                        p.issuedAt(), p.appealed(), p.expiredAt());
                return copy;
            }
        } catch (final Exception e) {
            plugin.getLogger().severe("CoreBan insert failed: " + e.getMessage());
        }
        return p;
    }

    @Override
    public Punishment getActive(final UUID target, final PunishmentType type) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM punishments WHERE target=? AND type=? ORDER BY issued_at DESC LIMIT 1")) {
            ps.setString(1, target.toString());
            ps.setString(2, type.name());
            final ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            final Punishment p = map(rs);
            final long now = System.currentTimeMillis() / 1000;
            if (p.durationSeconds() != -1 && p.expiredAt() > 0 && p.expiredAt() <= now) {
                return null;
            }
            return p;
        } catch (final Exception e) {
            plugin.getLogger().severe("CoreBan getActive failed: " + e.getMessage());
            return null;
        }
    }

    @Override
    public List<Punishment> history(final UUID target) {
        final List<Punishment> out = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM punishments WHERE target=? ORDER BY issued_at DESC")) {
            ps.setString(1, target.toString());
            final ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                out.add(map(rs));
            }
        } catch (final Exception e) {
            plugin.getLogger().severe("CoreBan history failed: " + e.getMessage());
        }
        return out;
    }

    @Override
    public int offenceCount(final UUID target, final PunishmentType type, final int tier) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM punishments WHERE target=? AND type=? AND tier=?")) {
            ps.setString(1, target.toString());
            ps.setString(2, type.name());
            ps.setInt(3, tier);
            final ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (final Exception e) {
            plugin.getLogger().severe("CoreBan offenceCount failed: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public void setExpired(final int id, final long expiredAt) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE punishments SET expired_at=? WHERE id=?")) {
            ps.setLong(1, expiredAt);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (final Exception e) {
            plugin.getLogger().severe("CoreBan setExpired failed: " + e.getMessage());
        }
    }

    @Override
    public void setAppealed(final int id, final boolean appealed) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE punishments SET appealed=? WHERE id=?")) {
            ps.setInt(1, appealed ? 1 : 0);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (final Exception e) {
            plugin.getLogger().severe("CoreBan setAppealed failed: " + e.getMessage());
        }
    }

    @Override
    public void close() {}

    private Punishment map(final ResultSet rs) throws java.sql.SQLException {
        return new Punishment(
                rs.getInt("id"),
                UUID.fromString(rs.getString("target")),
                PunishmentType.valueOf(rs.getString("type")),
                rs.getInt("tier"),
                rs.getInt("offence"),
                rs.getLong("duration"),
                rs.getString("reason"),
                rs.getString("staff"),
                rs.getLong("issued_at"),
                rs.getInt("appealed") == 1,
                rs.getLong("expired_at"));
    }
}
