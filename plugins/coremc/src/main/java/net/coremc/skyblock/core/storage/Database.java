package net.coremc.skyblock.core.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thin JDBC wrapper. Uses the SQLite/MySQL driver loaded by the server runtime,
 * so no driver jar is bundled (keeps the plugin lightweight).
 */
public final class Database {

    private final Logger logger;
    private final String url;
    private final String user;
    private final String password;

    public Database(final String url, final String user, final String password, final Logger logger) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.logger = logger;
    }

    public String url() { return url; }
    public String user() { return user; }
    public String password() { return password; }

    public boolean init() {
        try {
            final String driverClass = url.startsWith("jdbc:sqlite")
                    ? "org.sqlite.JDBC" : "com.mysql.cj.jdbc.Driver";
            Class.forName(driverClass);
            try (Connection c = getConnection()) {
                return c != null && !c.isClosed();
            }
        } catch (final ClassNotFoundException e) {
            logger.log(Level.SEVERE, "JDBC driver not found: " + e.getMessage());
            return false;
        } catch (final SQLException e) {
            logger.log(Level.SEVERE, "Could not connect to database: " + e.getMessage());
            return false;
        }
    }

    public Connection getConnection() throws SQLException {
        if (user == null || user.isEmpty()) {
            return java.sql.DriverManager.getConnection(url);
        }
        return java.sql.DriverManager.getConnection(url, user, password);
    }

    public void close() {
        // Connections are per-use; pool not needed at this scale.
    }
}
