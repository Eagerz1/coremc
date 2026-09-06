package net.coremc.store.api;

import net.coremc.foundation.CoreFoundation;
import net.coremc.store.StoreProducts;
import net.coremc.store.credits.CreditsManager;
import net.coremc.store.storage.StoreStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/**
 * Public store API used by CoreStore and external integrations.
 *
 * <p>All reward delivery is transactional: a purchase must be COMPLETED exactly
 * once. Physical items that don't fit are stored as pending rewards and claimed
 * later via {@link #claimPendingRewards(Player)}.</p>
 */
public final class CoreStoreApi {

    private final JavaPlugin plugin;
    private final StoreStorage storage;
    private final CreditsManager credits;
    private final StoreProducts products;

    public CoreStoreApi(final JavaPlugin plugin, final StoreStorage storage,
                        final CreditsManager credits) {
        this.plugin = plugin;
        this.storage = storage;
        this.credits = credits;
        this.products = new StoreProducts(plugin);
    }

    public StoreProducts products() {
        return products;
    }

    // ---- credits ----
    public double getCredits(final UUID uuid) { return credits.getCredits(uuid); }
    public void addCredits(final UUID uuid, final double amount, final String reason,
                           final java.util.function.Consumer<Double> then) {
        credits.addCredits(uuid, amount, reason, b -> {
            final Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                new CreditChangeEvent(p, amount, b).callEvent();
            }
            if (then != null) {
                then.accept(b);
            }
        });
    }
    public void removeCredits(final UUID uuid, final double amount, final String reason,
                              final java.util.function.Consumer<Double> then) {
        credits.removeCredits(uuid, amount, reason, b -> {
            final Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                new CreditChangeEvent(p, -amount, b).callEvent();
            }
            if (then != null) {
                then.accept(b);
            }
        });
    }
    public void setCredits(final UUID uuid, final double amount, final String reason) {
        credits.setCredits(uuid, amount, reason);
    }

    // ---- purchases ----
    public Purchase createPurchase(final Player player, final Purchase.Type type,
                                  final String productId, final String productName, final double price,
                                  final String transactionId) {
        final String id = "P-" + System.currentTimeMillis() + "-" + player.getUniqueId().toString().substring(0, 8);
        final Purchase p = new Purchase(id, player.getUniqueId(), player.getName(),
                productId, type, productName, price, transactionId, System.currentTimeMillis(),
                Purchase.Status.PENDING);
        storage.insertPurchase(p);
        new PurchaseCreateEvent(player, p).callEvent();
        return p;
    }

    public Purchase getPurchase(final String id) {
        return storage.getPurchase(id);
    }

    public List<Purchase> getPurchaseHistory(final UUID uuid, final int limit) {
        return storage.getHistory(uuid, limit);
    }

    /** Complete a purchase: verify, deliver rewards, mark COMPLETED. */
    public void completePurchase(final Player player, final Purchase purchase) {
        if (purchase.status() == Purchase.Status.COMPLETED
                || purchase.status() == Purchase.Status.REFUNDED) {
            return;
        }
        deliver(player, purchase);
        purchase.status(Purchase.Status.COMPLETED);
        storage.updateStatus(purchase.id(), Purchase.Status.COMPLETED);
        new PurchaseCompleteEvent(player, purchase).callEvent();
        announce(player, purchase);
    }

    public void refundPurchase(final Player player, final Purchase purchase) {
        if (purchase.status() == Purchase.Status.REFUNDED) {
            return;
        }
        purchase.status(Purchase.Status.REFUNDED);
        storage.updateStatus(purchase.id(), Purchase.Status.REFUNDED);
        // refund price as credits
        addCredits(player.getUniqueId(), purchase.price(), "Refund: " + purchase.productName(), null);
        new PurchaseRefundEvent(player, purchase).callEvent();
    }

    public List<String> getPendingRewards(final UUID uuid) {
        return storage.getPending(uuid);
    }

    public void claimPendingRewards(final Player player) {
        final List<String> pending = storage.getPending(player.getUniqueId());
        if (pending.isEmpty()) {
            CoreFoundation.getInstance().messages().sendRaw(player,
                    CoreFoundation.getInstance().messages().getPrefix() + " <gray>Nothing to claim.");
            return;
        }
        int claimed = 0;
        for (final String reward : pending) {
            if (deliverReward(player, reward)) {
                claimed++;
            }
        }
        storage.markPendingClaimed(player.getUniqueId());
        CoreFoundation.getInstance().messages().sendRaw(player,
                CoreFoundation.getInstance().messages().getPrefix()
                + " <green>Claimed " + claimed + " reward(s).");
    }

    // ---- product grants ----
    public void giveKey(final Player player, final String keyId) {
        if (!giveKeyViaCrate(player, keyId)) {
            return; // crate plugin unavailable -> do NOT consume
        }
        new KeyPurchaseEvent(player, keyId).callEvent();
    }

    public void giveBundle(final Player player, final String bundleId) {
        for (final StoreProducts.BundleDef b : products.bundles()) {
            if (b.id().equalsIgnoreCase(bundleId)) {
                for (final String line : b.contents()) {
                    deliverReward(player, line);
                }
                break;
            }
        }
        new BundlePurchaseEvent(player, bundleId).callEvent();
    }

    public void giveRank(final Player player, final String rankId) {
        for (final StoreProducts.RankDef r : products.ranks()) {
            if (r.id().equalsIgnoreCase(rankId)) {
                for (final String cmd : r.commands()) {
                    final String filled = cmd.replace("{player}", player.getName());
                    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), filled));
                }
                break;
            }
        }
        new RankPurchaseEvent(player, rankId).callEvent();
    }

    // ---- internals ----
    private void deliver(final Player player, final Purchase purchase) {
        switch (purchase.type()) {
            case KEY -> giveKey(player, purchase.productId());
            case BUNDLE -> giveBundle(player, purchase.productId());
            case RANK -> giveRank(player, purchase.productId());
        }
    }

    private void announce(final Player player, final Purchase purchase) {
        final String type = purchase.type().name().toLowerCase();
        if (!player.hasPermission("core.store.announce." + type) && !player.isOp()) {
            return;
        }
        if (!plugin.getConfig().getBoolean("announce.enabled", true)) {
            return;
        }
        final String fmt = plugin.getConfig().getString("announce.format",
                "<player> has purchased <product> — congratulations!");
        final String msg = fmt
                .replace("<player>", player.getName())
                .replace("<product>", purchase.productName());
        Bukkit.broadcastMessage(CoreFoundation.getInstance().messages().getPrefix() + " <white>" + msg);
    }

    /** Deliver one reward line. Returns false if it could not be given (space). */
    private boolean deliverReward(final Player player, final String line) {
        final String lower = line.toLowerCase();
        try {
            if (lower.startsWith("key:")) {
                giveKey(player, line.substring(4).trim());
                return true;
            }
            if (lower.startsWith("credits:")) {
                final double amt = Double.parseDouble(line.substring(8).trim());
                addCredits(player.getUniqueId(), amt, "Bundle reward", null);
                return true;
            }
            if (lower.startsWith("cmd:")) {
                final String filled = line.substring(4).trim().replace("{player}", player.getName());
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), filled));
                return true;
            }
            if (lower.startsWith("item:")) {
                final ItemStack stack = parseItem(line.substring(5).trim());
                if (stack == null) {
                    return false;
                }
                if (player.getInventory().firstEmpty() == -1) {
                    storage.addPendingReward(currentPurchaseId(player), line);
                    return false;
                }
                player.getInventory().addItem(stack);
                return true;
            }
            if (lower.startsWith("tag:")) {
                return giveTag(player, line.substring(4).trim());
            }
        } catch (final NumberFormatException e) {
            plugin.getLogger().warning("Bad reward line: " + line);
        }
        return false;
    }

    private String currentPurchaseId(final Player player) {
        return "PENDING-" + player.getUniqueId().toString().substring(0, 8);
    }

    private ItemStack parseItem(final String spec) {
        // spec: "MATERIAL:amount" e.g. "DIAMOND:5"
        final String[] parts = spec.split(":");
        final org.bukkit.Material mat = org.bukkit.Material.matchMaterial(parts[0]);
        if (mat == null) {
            return null;
        }
        final int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        return new ItemStack(mat, amount);
    }

    /** Hook into CoreTags to unlock a tag as a store reward (no hard dependency). */
    private boolean giveTag(final Player player, final String tagId) {
        try {
            final org.bukkit.plugin.java.JavaPlugin tags =
                    (org.bukkit.plugin.java.JavaPlugin) Bukkit.getPluginManager().getPlugin("core-tags");
            if (tags == null) {
                plugin.getLogger().warning("core-tags not present - tag '" + tagId + "' not delivered.");
                return false;
            }
            final Object api = tags.getClass().getMethod("api").invoke(tags);
            final java.lang.reflect.Method unlock = api.getClass().getMethod("unlockTag", Player.class, String.class);
            final boolean ok = (boolean) unlock.invoke(api, player, tagId);
            if (ok) {
                CoreFoundation.getInstance().messages().sendRaw(player,
                        CoreFoundation.getInstance().messages().getPrefix()
                        + " <green>You unlocked the " + tagId + " tag!");
            }
            return ok;
        } catch (final Throwable t) {
            plugin.getLogger().warning("Failed to give tag '" + tagId + "': " + t.getMessage());
            return false;
        }
    }

    /** Hook into the crate plugin's giveKey method via reflection (no hard dependency). */
    private boolean giveKeyViaCrate(final Player player, final String keyId) {
        try {
            final Class<?> apiClass = Class.forName("net.coremc.crate.CrateApi");
            final java.lang.reflect.Method get = apiClass.getMethod("getInstance");
            final Object api = get.invoke(null);
            final java.lang.reflect.Method give = apiClass.getMethod("giveKey", Player.class, String.class);
            give.invoke(api, player, keyId);
            return true;
        } catch (final ClassNotFoundException e) {
            plugin.getLogger().warning("Crate plugin not found - key '" + keyId + "' not delivered.");
            return false;
        } catch (final Throwable t) {
            plugin.getLogger().warning("Failed to give key '" + keyId + "': " + t.getMessage());
            return false;
        }
    }
}
