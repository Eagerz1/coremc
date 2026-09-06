package net.coremc.foundation.util;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple per-player, per-action cooldown system.
 *
 * <p>Keys are namespaced by an action id so multiple independent cooldowns can
 * coexist for the same player.</p>
 */
public final class CooldownManager {

    // (uuid:action) -> expiry epoch millis
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    private static @NotNull String key(final @NotNull UUID uuid, final @NotNull String action) {
        return uuid.toString() + ":" + action;
    }

    /**
     * Start a cooldown for a player + action.
     * @param seconds duration in seconds.
     */
    public void start(final @NotNull UUID uuid, final @NotNull String action, final long seconds) {
        cooldowns.put(key(uuid, action), System.currentTimeMillis() + seconds * 1000L);
    }

    /**
     * Check whether a player is still on cooldown for an action.
     */
    public boolean isOnCooldown(final @NotNull UUID uuid, final @NotNull String action) {
        final Long expiry = cooldowns.get(key(uuid, action));
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiry) {
            cooldowns.remove(key(uuid, action));
            return false;
        }
        return true;
    }

    /**
     * @return remaining seconds on the cooldown (0 if not on cooldown).
     */
    public long getRemaining(final @NotNull UUID uuid, final @NotNull String action) {
        final Long expiry = cooldowns.get(key(uuid, action));
        if (expiry == null) {
            return 0;
        }
        final long remaining = expiry - System.currentTimeMillis();
        return remaining <= 0 ? 0 : (long) Math.ceil(remaining / 1000.0);
    }

    /** Clear a specific cooldown. */
    public void clear(final @NotNull UUID uuid, final @NotNull String action) {
        cooldowns.remove(key(uuid, action));
    }

    /** Clear all cooldowns for a player. */
    public void clearAll(final @NotNull UUID uuid) {
        final String prefix = uuid.toString() + ":";
        cooldowns.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
