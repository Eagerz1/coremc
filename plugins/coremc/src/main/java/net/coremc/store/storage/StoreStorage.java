package net.coremc.store.storage;

import net.coremc.store.api.Purchase;
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
import java.util.logging.Level;

/** SQLite persistence for purchases, credits and pending rewards. */
public final class StoreStorage {

    private final JavaPlugin plugin;
    private Connection conn;

    public StoreStorage(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        final File f = new File(plugin.getDataFolder(), "store.db");
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath());
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS purchases ("
                            + "id TEXT PRIMARY KEY, uuid TEXT NOT NULL, name TEXT, product_id TEXT, "
                            + "type TEXT, product_name TEXT, price REAL, txn TEXT, created BIGINT, status TEXT)");
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS credit_tx ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, change REAL, "
                            + "balance REAL, reason TEXT, ts BIGINT)");
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS pending_rewards ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, purchase_id TEXT, reward TEXT, claimed INTEGER DEFAULT 0)");
            return true;
        } catch (final SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Store DB init failed", e);
            return false;
        }
    }

    // ---- async wrapper ----
    private void async(final Runnable r) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, r);
    }

    // ---- credits ----
    public double getCredits(final UUID uuid) {
        try (final PreparedStatement ps = conn.prepareStatement("SELECT balance FROM credit_tx WHERE uuid = ? ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, uuid.toString());
            final ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getDouble("balance");
            }
        } catch (final SQLException e) {
            plugin.getLogger().warning("getCredits failed: " + e.getMessage());
        }
        return 0.0;
    }

    public void setCredits(final UUID uuid, final double amount, final String reason) {
        async(() -> {
            final double clamped = Math.max(0, amount);
            try (final PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO credit_tx(uuid, change, balance, reason, ts) VALUES(?, ?, ?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setDouble(2, 0);
                ps.setDouble(3, clamped);
                ps.setString(4, reason);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (final SQLException e) {
                plugin.getLogger().warning("setCredits failed: " + e.getMessage());
            }
        });
    }

    /** Add (positive) or remove (negative) and return new balance via callback. */
    public void changeCredits(final UUID uuid, final double delta, final String reason,
                              final java.util.function.Consumer<Double> then) {
        async(() -> {
            final double newBal = Math.max(0, getCredits(uuid) + delta);
            try (final PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO credit_tx(uuid, change, balance, reason, ts) VALUES(?, ?, ?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setDouble(2, delta);
                ps.setDouble(3, newBal);
                ps.setString(4, reason);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (final SQLException e) {
                plugin.getLogger().warning("changeCredits failed: " + e.getMessage());
            }
            final double finalBal = newBal;
            if (then != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> then.accept(finalBal));
            }
        });
    }

    public List<String> creditHistory(final UUID uuid, final int limit) {
        final List<String> out = new ArrayList<>();
        try (final PreparedStatement ps = conn.prepareStatement(
                "SELECT change, reason, ts FROM credit_tx WHERE uuid = ? ORDER BY id DESC LIMIT ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            final ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                out.add(rs.getDouble("change") + "|" + rs.getString("reason") + "|" + rs.getLong("ts"));
            }
        } catch (final SQLException e) {
            plugin.getLogger().warning("creditHistory failed: " + e.getMessage());
        }
        return out;
    }

    // ---- purchases ----
    public void insertPurchase(final Purchase p) {
        async(() -> {
            try (final PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO purchases(id, uuid, name, product_id, type, product_name, price, txn, created, status) "
                            + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, p.id());
                ps.setString(2, p.playerUuid().toString());
                ps.setString(3, p.playerName());
                ps.setString(4, p.productId());
                ps.setString(5, p.type().name());
                ps.setString(6, p.productName());
                ps.setDouble(7, p.price());
                ps.setString(8, p.transactionId());
                ps.setLong(9, p.createdAt());
                ps.setString(10, p.status().name());
                ps.executeUpdate();
            } catch (final SQLException e) {
                plugin.getLogger().warning("insertPurchase failed: " + e.getMessage());
            }
        });
    }

    public void updateStatus(final String id, final Purchase.Status status) {
        async(() -> {
            try (final PreparedStatement ps = conn.prepareStatement("UPDATE purchases SET status = ? WHERE id = ?")) {
                ps.setString(1, status.name());
                ps.setString(2, id);
                ps.executeUpdate();
            } catch (final SQLException e) {
                plugin.getLogger().warning("updateStatus failed: " + e.getMessage());
            }
        });
    }

    public Purchase getPurchase(final String id) {
        try (final PreparedStatement ps = conn.prepareStatement("SELECT * FROM purchases WHERE id = ?")) {
            ps.setString(1, id);
            return mapPurchase(ps.executeQuery());
        } catch (final SQLException e) {
            plugin.getLogger().warning("getPurchase failed: " + e.getMessage());
            return null;
        }
    }

    public List<Purchase> getHistory(final UUID uuid, final int limit) {
        final List<Purchase> out = new ArrayList<>();
        try (final PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM purchases WHERE uuid = ? ORDER BY created DESC LIMIT ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            final ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                out.add(mapPurchase(rs));
            }
        } catch (final SQLException e) {
            plugin.getLogger().warning("getHistory failed: " + e.getMessage());
        }
        return out;
    }

    private Purchase mapPurchase(final ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return null;
        }
        return new Purchase(
                rs.getString("id"),
                UUID.fromString(rs.getString("uuid")),
                rs.getString("name"),
                rs.getString("product_id"),
                Purchase.Type.valueOf(rs.getString("type")),
                rs.getString("product_name"),
                rs.getDouble("price"),
                rs.getString("txn"),
                rs.getLong("created"),
                Purchase.Status.valueOf(rs.getString("status")));
    }

    // ---- pending rewards (undelivered items) ----
    public void addPendingReward(final String purchaseId, final String reward) {
        async(() -> {
            try (final PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO pending_rewards(purchase_id, reward) VALUES(?, ?)")) {
                ps.setString(1, purchaseId);
                ps.setString(2, reward);
                ps.executeUpdate();
            } catch (final SQLException e) {
                plugin.getLogger().warning("addPendingReward failed: " + e.getMessage());
            }
        });
    }

    public List<String> getPending(final UUID uuid) {
        final List<String> out = new ArrayList<>();
        try (final PreparedStatement ps = conn.prepareStatement(
                "SELECT r.reward FROM pending_rewards r JOIN purchases p ON p.id = r.purchase_id "
                        + "WHERE p.uuid = ? AND r.claimed = 0")) {
            ps.setString(1, uuid.toString());
            final ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                out.add(rs.getString("reward"));
            }
        } catch (final SQLException e) {
            plugin.getLogger().warning("getPending failed: " + e.getMessage());
        }
        return out;
    }

    public void markPendingClaimed(final UUID uuid) {
        async(() -> {
            try (final PreparedStatement ps = conn.prepareStatement(
                    "UPDATE pending_rewards SET claimed = 1 WHERE purchase_id IN "
                            + "(SELECT id FROM purchases WHERE uuid = ?)")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (final SQLException e) {
                plugin.getLogger().warning("markPendingClaimed failed: " + e.getMessage());
            }
        });
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
