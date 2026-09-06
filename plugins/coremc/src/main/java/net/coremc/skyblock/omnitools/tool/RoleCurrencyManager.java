package net.coremc.skyblock.omnitools.tool;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player role currencies, earned by performing each role's activity.
 *
 * <p>Currencies are intentionally separate from Money, Credits and Sky Tokens —
 * they exist only to upgrade Omnitools. The set of roles is driven by
 * {@code omnitools.roles} in config, so adding a new role later only requires a
 * new entry there (plus a matching Omnitool material); no code change needed.</p>
 */
public final class RoleCurrencyManager {

    /** Canonical role ids. These map 1:1 to the Omnitool roles (incl. UNIVERSAL). */
    public static final List<String> ROLES = List.of("MINING", "FISHING", "FARMING", "LOGGING", "SLAYING", "UNIVERSAL");

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    // key = uuid + ":" + ROLE -> amount
    private final Map<String, Long> cache = new ConcurrentHashMap<>();

    public RoleCurrencyManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "role-currency.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        for (final String k : data.getKeys(false)) {
            if (k.contains(":")) cache.put(k, data.getLong(k));
        }
    }

    private static String key(final UUID uuid, final String role) {
        return uuid.toString() + ":" + role.toUpperCase(java.util.Locale.ROOT);
    }

    public long get(final UUID uuid, final String role) {
        return cache.getOrDefault(key(uuid, role), 0L);
    }

    public void add(final UUID uuid, final String role, final long amount) {
        if (amount <= 0) return;
        // Honour the live "2x Slaying Coins" event multiplier (SLAYING role only), applied centrally.
        final long boosted = Math.round(amount * net.coremc.skyblock.events.Events.roleCurrencyMultiplier(role));
        final String k = key(uuid, role);
        cache.put(k, get(uuid, role) + boosted);
        data.set(k, cache.get(k));
        saveSoon();
    }

    /** Take up to {@code amount}; returns the amount actually taken. */
    public long take(final UUID uuid, final String role, final long amount) {
        if (amount <= 0) return 0;
        final String k = key(uuid, role);
        final long have = get(uuid, role);
        final long taken = Math.min(have, amount);
        if (taken <= 0) return 0;
        cache.put(k, have - taken);
        data.set(k, have - taken);
        saveSoon();
        return taken;
    }

    public boolean canAfford(final UUID uuid, final String role, final long amount) {
        return get(uuid, role) >= amount;
    }

    private void saveSoon() {
        try {
            data.save(file);
        } catch (final java.io.IOException e) {
            plugin.getLogger().warning("Could not save role-currency.yml: " + e.getMessage());
        }
    }

    public void saveAll() {
        saveSoon();
    }
}
