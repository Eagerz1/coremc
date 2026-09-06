package net.coremc.foundation.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Best-effort bridge to the CoreMC-Store Credits service.
 *
 * <p>Credits are owned by the standalone {@code CoreMC-Store} plugin and exposed
 * through {@code net.coremc.store.CreditsService#getCredits(OfflinePlayer)}. This
 * plugin never stores credit balances itself — it only reads them when the store
 * is present. When CoreMC-Store is absent (or the bridge fails to initialise) every
 * read returns {@code 0}, so callers can safely show a fallback without a second
 * data system.</p>
 */
public final class CreditsBridge {

    private final JavaPlugin plugin;
    private Object creditsApi;   // CreditsService instance
    private Method creditsGet;   // long getCredits(OfflinePlayer)

    public CreditsBridge(final JavaPlugin plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        try {
            final var pm = Bukkit.getPluginManager();
            final var store = pm.getPlugin("CoreMC-Store");
            if (store == null) {
                plugin.getLogger().info("[credits] CoreMC-Store not found; credits placeholders return 0.");
                return;
            }
            final Class<?> cs = Class.forName("net.coremc.store.CreditsService");
            final Method mCredits = store.getClass().getMethod("credits");
            creditsApi = mCredits.invoke(store);
            creditsGet = cs.getMethod("getCredits", OfflinePlayer.class);
            plugin.getLogger().info("[credits] CoreMC-Store credits bridge active.");
        } catch (final Throwable t) {
            plugin.getLogger().warning("[credits] bridge failed (non-fatal): " + t.getMessage());
            creditsApi = null;
            creditsGet = null;
        }
    }

    /** True when the CoreMC-Store credits service is reachable. */
    public boolean available() {
        return creditsApi != null && creditsGet != null;
    }

    /** Player's current Credits balance, or {@code 0} when unavailable / on error. */
    public long getCredits(final UUID uuid) {
        if (!available()) {
            return 0L;
        }
        try {
            return (long) creditsGet.invoke(creditsApi, Bukkit.getOfflinePlayer(uuid));
        } catch (final Throwable t) {
            return 0L;
        }
    }
}
