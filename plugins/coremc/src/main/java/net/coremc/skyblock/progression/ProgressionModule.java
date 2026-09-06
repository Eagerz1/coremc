package net.coremc.skyblock.progression;
import net.coremc.coremc.CoreMC;

import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.util.CreditsBridge;
import net.coremc.foundation.util.Economy;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.api.ProgressionProvider;
import net.coremc.skyblock.core.storage.Island;
import net.coremc.skyblock.progression.api.ProgressionApi;
import net.coremc.skyblock.progression.buff.BuffManager;
import net.coremc.skyblock.progression.command.BuffsCommand;
import net.coremc.skyblock.progression.command.RoleCommand;
import net.coremc.skyblock.progression.command.SkyTokensCommand;
import net.coremc.skyblock.progression.command.UpgradesCommand;
import net.coremc.skyblock.progression.listener.ActivityListener;
import net.coremc.skyblock.progression.role.RoleManager;
import net.coremc.skyblock.progression.role.RoleProgression;
import net.coremc.skyblock.progression.token.TokenManager;
import net.coremc.skyblock.progression.tree.TreeManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sky Tokens, role progression, island upgrades and island-wide buffs. */
public final class ProgressionModule implements ProgressionProvider {

    private final JavaPlugin plugin;
    private IslandApi islandApi;
    private TokenManager tokens;
    private RoleManager roles;
    private TreeManager trees;
    private BuffManager buffs;
    private ProgressionApi api;
    private Economy economy;
    private CreditsBridge credits;
    private RoleProgression roleProgression;
    private org.bukkit.command.CommandExecutor upgradesExecutor;
    private org.bukkit.command.CommandExecutor buffsExecutor;
    private BukkitTask autoSave;

    public ProgressionModule(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        CoreFoundation.getInstance().debug("ProgressionModule enabling");

        final net.coremc.skyblock.core.IslandModule core = net.coremc.coremc.CoreMC.getInstance().islands();
        islandApi = core.api();
        ((net.coremc.skyblock.core.api.IslandApi) islandApi).setProgressionProvider(this);
        core.setProgressionProvider(this);

        this.tokens = new TokenManager(plugin);
        this.roles = new RoleManager(plugin);
        this.trees = new TreeManager(plugin);
        this.buffs = new BuffManager(plugin, trees);
        this.api = new ProgressionApi(plugin, tokens, roles, trees);
        this.economy = new Economy(plugin);
        this.credits = new CreditsBridge(plugin);
        this.roleProgression = new RoleProgression(this, plugin);
        this.roleProgression.reload();

        plugin.getCommand("upgrades").setExecutor(new UpgradesCommand(plugin, this));
        plugin.getCommand("upgrades").setTabCompleter(new UpgradesCommand(plugin, this));
        plugin.getCommand("buffs").setExecutor(new BuffsCommand(plugin, this));
        plugin.getCommand("roles").setExecutor(new RoleCommand(plugin, this));
        plugin.getCommand("skytokens").setExecutor(new SkyTokensCommand(this));
        plugin.getCommand("skytokens").setTabCompleter(new SkyTokensCommand(this));
        this.upgradesExecutor = plugin.getCommand("upgrades").getExecutor();
        this.buffsExecutor = plugin.getCommand("buffs").getExecutor();

        Bukkit.getPluginManager().registerEvents(new ActivityListener(plugin, this), plugin);

        registerPlaceholders();

        autoSave = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            tokens.saveAll();
            roles.save();
        }, 6000L, 6000L);

        plugin.getLogger().info("ProgressionModule enabled.");
    }

    public void shutdown() {
        if (autoSave != null) {
            autoSave.cancel();
        }
        tokens.saveAll();
        roles.save();
    }

    /**
     * Register PlaceholderAPI placeholders backed by the existing CoreMC systems
     * (no duplicate data). All four are exposed under the {@code coremc} expansion
     * identifier, i.e. {@code %coremc_credits%}, {@code %coremc_tokens%},
     * {@code %coremc_money%} and {@code %coremc_island_level%}, and work anywhere
     * PlaceholderAPI is supported (scoreboards, GUIs, chat, other plugins).
     *
     * <p>Every placeholder returns a safe fallback when the value is missing:
     * {@code 0} for credits/tokens/money and {@code 0} for island level when the
     * player has no island. A null player (server/console context) yields the
     * zero fallback as well.</p>
     */
    private void registerPlaceholders() {
        // Existing identifiers (kept for backwards compatibility).
        net.coremc.foundation.PlaceholderHook.set("coremc_tokens", player -> {
            if (player == null) return "0";
            return String.format(java.util.Locale.ROOT, "%,d", tokens().get(player.getUniqueId()));
        });
        net.coremc.foundation.PlaceholderHook.set("coremc_island", player -> {
            if (player == null) return "-";
            final net.coremc.skyblock.core.storage.Island is = islandApi.getIsland(player.getUniqueId());
            return is == null ? "-" : String.valueOf(is.getId());
        });
        net.coremc.foundation.PlaceholderHook.set("coremc_level", player -> {
            if (player == null) return "0";
            final net.coremc.skyblock.core.storage.Island is = islandApi.getIsland(player.getUniqueId());
            return is == null ? "0" : String.valueOf(is.getLevel());
        });

        // --- CoreMC placeholder expansion (single source: PlayerDataManager) ---
        final var pdm = net.coremc.coremc.CoreMC.getInstance().playerDataManager();
        net.coremc.foundation.PlaceholderHook.set("credits", player -> {
            if (player == null) return "0";
            return String.format(java.util.Locale.ROOT, "%,d", pdm.getCredits(player.getUniqueId()));
        });
        net.coremc.foundation.PlaceholderHook.set("tokens", player -> {
            if (player == null) return "0";
            return String.format(java.util.Locale.ROOT, "%,d", pdm.getSkyTokens(player.getUniqueId()));
        });
        net.coremc.foundation.PlaceholderHook.set("money", player -> {
            if (player == null) return "0";
            final double bal = economy.getBalance(player.getUniqueId());
            return String.format(java.util.Locale.ROOT, "%,.2f", bal);
        });
        net.coremc.foundation.PlaceholderHook.set("island_level", player -> {
            if (player == null) return "0";
            final net.coremc.skyblock.core.storage.Island is = islandApi.getIsland(player.getUniqueId());
            if (is == null) return "0";
            return String.valueOf(api().islandLevel(is.getId()));
        });
    }

    public IslandApi islandApi() { return islandApi; }
    public TokenManager tokens() { return tokens; }
    public RoleManager roles() { return roles; }
    public net.coremc.skyblock.progression.role.RoleSetManager roleSets() { return CoreMC.getInstance().roleSets(); }
    public TreeManager trees() { return trees; }
    public BuffManager buffs() { return buffs; }
    public ProgressionApi api() { return api; }
    public RoleProgression roleProgression() { return roleProgression; }

    /** Executor for /is upgrades (used by the island command to open the menu). */
    public org.bukkit.command.CommandExecutor getUpgradesExecutor() { return upgradesExecutor; }
    /** Executor for /is buffs. */
    public org.bukkit.command.CommandExecutor getBuffsExecutor() { return buffsExecutor; }

    @Override
    public boolean hasUpgrade(final int islandId, final String upgradeId) {
        return trees.getLevel(String.valueOf(islandId), "island", upgradeId) >= 1;
    }

    @Override
    public int getUpgradeLevel(final int islandId, final String upgradeId) {
        return trees.getLevel(String.valueOf(islandId), "island", upgradeId);
    }

    @Override
    public double getBuffMultiplier(final int islandId, final String buffId) {
        return buffs.getMultiplier(islandId, buffId);
    }
}
