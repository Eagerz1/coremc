package net.coremc.store.credits;

import net.coremc.store.storage.StoreStorage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/** Player Credits balance wrapper around StoreStorage. */
public final class CreditsManager {

    private final JavaPlugin plugin;
    private final StoreStorage storage;

    public CreditsManager(final JavaPlugin plugin, final StoreStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public double getCredits(final UUID uuid) {
        return storage.getCredits(uuid);
    }

    /** Add (or remove when negative) credits. Returns new balance to the callback on the main thread. */
    public void addCredits(final UUID uuid, final double amount, final String reason,
                           final java.util.function.Consumer<Double> then) {
        storage.changeCredits(uuid, Math.abs(amount), reason, then);
    }

    public void removeCredits(final UUID uuid, final double amount, final String reason,
                              final java.util.function.Consumer<Double> then) {
        storage.changeCredits(uuid, -Math.abs(amount), reason, then);
    }

    public void setCredits(final UUID uuid, final double amount, final String reason) {
        storage.setCredits(uuid, amount, reason);
    }

    public List<String> history(final UUID uuid, final int limit) {
        return storage.creditHistory(uuid, limit);
    }
}
