package net.coremc.store;

import net.coremc.foundation.CoreFoundation;
import net.coremc.store.api.CoreStoreApi;
import net.coremc.store.api.PurchaseCompleteEvent;
import net.coremc.store.api.PurchaseCreateEvent;
import net.coremc.store.api.PurchaseRefundEvent;
import net.coremc.store.api.KeyPurchaseEvent;
import net.coremc.store.api.BundlePurchaseEvent;
import net.coremc.store.api.RankPurchaseEvent;
import net.coremc.store.api.CreditChangeEvent;
import net.coremc.store.command.CreditsCommand;
import net.coremc.store.command.StoreClaimCommand;
import net.coremc.store.command.StoreCommand;
import net.coremc.store.credits.CreditsManager;
import net.coremc.store.gui.StoreGui;
import net.coremc.store.storage.StoreStorage;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * CoreMC in-game store: keys, bundles, ranks, credits and crate API.
 *
 * <p>Products are fully config-driven (keys.yml, bundles.yml, ranks.yml). Actual
 * crate opening and rank permission handling live in other plugins; CoreStore
 * only sells, records, announces and delivers via a clean API.</p>
 */
public final class CoreStore extends JavaPlugin {

    private static CoreStore instance;
    private StoreStorage storage;
    private CreditsManager credits;
    private CoreStoreApi api;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        CoreFoundation.getInstance().debug("CoreStore enabling");

        this.storage = new StoreStorage(this);
        if (!storage.init()) {
            getLogger().log(Level.SEVERE, "Could not init store storage - disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.credits = new CreditsManager(this, storage);
        this.api = new CoreStoreApi(this, storage, credits);

        getCommand("store").setExecutor(new StoreCommand(this, storage, api));
        getCommand("store").setTabCompleter(new StoreCommand(this, storage, api));
        getCommand("credits").setExecutor(new CreditsCommand(this, credits));
        getCommand("credits").setTabCompleter(new CreditsCommand(this, credits));
        getCommand("storeclaim").setExecutor(new StoreClaimCommand(this, storage, credits));

        registerPlaceholders();

        getLogger().info("CoreStore v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.shutdown();
        }
        instance = null;
        getLogger().info("CoreStore disabled.");
    }

    public static CoreStore getInstance() {
        return instance;
    }

    public StoreStorage storage() {
        return storage;
    }

    public CreditsManager credits() {
        return credits;
    }

    public CoreStoreApi api() {
        return api;
    }

    /**
     * Register the TAB/PlaceholderAPI credit placeholder(s). Without this the
     * %coremc_credits% token used by the TAB tab-list listener renders blank.
     */
    private void registerPlaceholders() {
        net.coremc.foundation.PlaceholderHook.set("coremc_credits", player -> {
            if (player == null) return "0";
            final double bal = credits().getCredits(player.getUniqueId());
            return String.format(java.util.Locale.ROOT, "%,.0f", bal);
        });
    }
}
