package net.coremc.foundation.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import net.coremc.coremc.CoreMC;

import java.util.UUID;

/**
 * Shared CoreMC economy. Spends the server's real money (Vault/Essentials) when an
 * economy provider is present, otherwise falls back to Sky Tokens so purchasers
 * never see a silent failure. No second currency is introduced — tokens are the
 * existing progression currency, used only when no Vault economy is installed.
 */
public final class Economy {

    private final JavaPlugin plugin;

    public Economy(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Whether a Vault economy provider is registered. */
    public boolean vaultPresent() {
        return Bukkit.getPluginManager().getPlugin("Vault") != null
                && getEconomy() != null;
    }

    /** Get the player's balance in the active currency (money, or tokens if no Vault). */
    public double getBalance(final UUID uuid) {
        final OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        if (vaultPresent()) {
            try {
                return (double) getEconomy().getMethod("getBalance", OfflinePlayer.class)
                        .invoke(getEconomyProvider(), p);
            } catch (final ReflectiveOperationException e) {
                plugin.getLogger().warning("Vault getBalance failed: " + e.getMessage());
            }
        }
        return (double) CoreMC_tokens().get(uuid);
    }

    /** True if the player can afford {@code price}. */
    public boolean has(final UUID uuid, final double price) {
        return getBalance(uuid) >= price;
    }

    /**
     * Charge {@code price} from the player. Returns true on success (balance checked
     * and deducted). On Vault it withdraws the real money balance; otherwise it
     * removes Sky Tokens. Returns false if insufficient.
     */
    public boolean charge(final UUID uuid, final double price, final String reason) {
        if (price <= 0) {
            return true;
        }
        if (!has(uuid, price)) {
            return false;
        }
        final OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        if (vaultPresent()) {
            try {
                getEconomy().getMethod("withdrawPlayer", OfflinePlayer.class, double.class)
                        .invoke(getEconomyProvider(), p, price);
                return true;
            } catch (final ReflectiveOperationException e) {
                plugin.getLogger().warning("Vault withdraw failed: " + e.getMessage());
                return false;
            }
        }
        // Fallback: Sky Tokens (now owned by the persistent PlayerDataManager).
        CoreMC.getInstance().playerDataManager().removeSkyTokens(uuid, (long) price, reason, "economy");
        return true;
    }

    /** Add {@code amount} to the player's balance (Vault money, or Sky Tokens fallback). */
    public void addMoney(final UUID uuid, final double amount) {
        if (amount <= 0) {
            return;
        }
        final OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        if (vaultPresent()) {
            try {
                getEconomy().getMethod("depositPlayer", OfflinePlayer.class, double.class)
                        .invoke(getEconomyProvider(), p, amount);
                return;
            } catch (final ReflectiveOperationException e) {
                plugin.getLogger().warning("Vault deposit failed: " + e.getMessage());
            }
        }
        CoreMC.getInstance().playerDataManager().addSkyTokens(uuid, (long) amount, "Crate reward", "economy");
    }

    // --- reflection helpers for Vault ---
    private Object getEconomyProvider() {
        try {
            final Class<?> econClass = Class.forName("net.milkbowl.vault.economy.Economy");
            final var reg = Bukkit.getServicesManager().getRegistration(econClass);
            return reg == null ? null : reg.getProvider();
        } catch (final ReflectiveOperationException e) {
            return null;
        }
    }

    private Class<?> getEconomy() {
        try {
            return Class.forName("net.milkbowl.vault.economy.Economy");
        } catch (final ReflectiveOperationException e) {
            return null;
        }
    }

    // --- Sky Token fallback (existing CoreMC progression currency) ---
    @SuppressWarnings("unchecked")
    private net.coremc.skyblock.progression.token.TokenManager CoreMC_tokens() {
        return net.coremc.coremc.CoreMC.getInstance().progression().tokens();
    }
}
