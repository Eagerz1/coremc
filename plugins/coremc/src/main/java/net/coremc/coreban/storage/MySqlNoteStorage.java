package net.coremc.coreban.storage;

import net.coremc.coreban.model.Note;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MySQL-backed staff notes storage. Same schema as SQLite, same DB instance
 * as the punishment storage.
 */
public final class MySqlNoteStorage implements NoteStorage {

    private final String url;
    private final String user;
    private final String password;

    public MySqlNoteStorage(final String host, final int port, final String database,
                            final String user, final String password) {
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC";
        this.user = user;
        this.password = password;
    }

    private Connection conn() throws java.sql.SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    public void init() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection c = conn(); Statement s = c.createStatement()) {
                s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS notes (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        target VARCHAR(36) NOT NULL,
                        player_name VARCHAR(36) NOT NULL,
                        content TEXT NOT NULL,
                        staff VARCHAR(36) NOT NULL,
                        created_at BIGINT NOT NULL
                    )""");
            }
        } catch (final Exception e) {
            System.err.println("[CoreBan] MySQL notes init failed: " + e.getMessage());
        }
    }

    @Override
    public Note insert(final Note n) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO notes (target,player_name,content,staff,created_at) "
                        + "VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, n.target().toString());
            ps.setString(2, n.playerName());
            ps.setString(3, n.content());
            ps.setString(4, n.staff());
            ps.setLong(5, n.createdAt());
            ps.executeUpdate();
            final ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return new Note(keys.getInt(1), n.target(), n.playerName(),
                        n.content(), n.staff(), n.createdAt());
            }
        } catch (final Exception e) {
            System.err.println("[CoreBan] note insert failed: " + e.getMessage());
        }
        return n;
    }

    @Override
    public List<Note> notes(final UUID target) {
        final List<Note> out = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM notes WHERE target=? ORDER BY created_at DESC")) {
            ps.setString(1, target.toString());
            final ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                out.add(map(rs));
            }
        } catch (final Exception e) {
            System.err.println("[CoreBan] notes list failed: " + e.getMessage());
        }
        return out;
    }

    @Override
    public Note get(final UUID target, final int noteId) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM notes WHERE target=? AND id=?")) {
            ps.setString(1, target.toString());
            ps.setInt(2, noteId);
            final ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return map(rs);
            }
        } catch (final Exception e) {
            System.err.println("[CoreBan] note get failed: " + e.getMessage());
        }
        return null;
    }

    @Override
    public boolean remove(final UUID target, final int noteId) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM notes WHERE target=? AND id=?")) {
            ps.setString(1, target.toString());
            ps.setInt(2, noteId);
            return ps.executeUpdate() > 0;
        } catch (final Exception e) {
            System.err.println("[CoreBan] note remove failed: " + e.getMessage());
        }
        return false;
    }

    @Override
    public void close() {}

    private Note map(final ResultSet rs) throws java.sql.SQLException {
        return new Note(
                rs.getInt("id"),
                UUID.fromString(rs.getString("target")),
                rs.getString("player_name"),
                rs.getString("content"),
                rs.getString("staff"),
                rs.getLong("created_at"));
    }
}
