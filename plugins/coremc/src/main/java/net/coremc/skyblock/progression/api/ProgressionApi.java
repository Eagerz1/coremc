package net.coremc.skyblock.progression.api;

import net.coremc.skyblock.progression.role.RoleManager;
import net.coremc.skyblock.progression.token.TokenManager;
import net.coremc.skyblock.progression.tree.TreeManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Public API for awarding Sky Tokens, role XP and island XP, and for reading
 * progression. Other CoreMC plugins (gens, spawners, omnitools, tools) call
 * these methods so progression stays centralised. All awards fire events.
 */
public final class ProgressionApi {

    private final JavaPlugin plugin;
    private final TokenManager tokens;
    private final RoleManager roles;
    private final TreeManager trees;

    public ProgressionApi(final JavaPlugin plugin, final TokenManager tokens,
                          final RoleManager roles, final TreeManager trees) {
        this.plugin = plugin;
        this.tokens = tokens;
        this.roles = roles;
        this.trees = trees;
    }

    public TokenManager tokens() { return tokens; }
    public RoleManager roles() { return roles; }
    public TreeManager trees() { return trees; }

    /** Award Sky Tokens to a player (after island buff multiplier already applied by caller). */
    public long awardTokens(final UUID uuid, final long amount) {
        if (amount <= 0) {
            return tokens.get(uuid);
        }
        final TokenEarnEvent ev = new TokenEarnEvent(uuid, amount);
        plugin.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return tokens.get(uuid);
        }
        tokens.add(uuid, ev.getAmount());
        return tokens.get(uuid);
    }

    public long getTokens(final UUID uuid) {
        return tokens.get(uuid);
    }

    public boolean takeTokens(final UUID uuid, final long amount) {
        return tokens.take(uuid, amount);
    }

    /** Award role XP to a player for a given tree (mining/farming/fishing/logging/slaying). */
    public void awardRoleXp(final UUID uuid, final String tree, final long amount) {
        if (amount <= 0) {
            return;
        }
        final RoleXpEvent ev = new RoleXpEvent(uuid, tree, amount);
        plugin.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return;
        }
        roles.addXp(uuid, ev.getTree(), ev.getAmount());
    }

    /** Add island XP (shared) — stored as a pseudo tree on the island scope.
     *  Honours the live "2x Island XP" event multiplier centrally. */
    public void awardIslandXp(final int islandId, final long amount) {
        if (amount <= 0) {
            return;
        }
        final long boosted = Math.round(amount * net.coremc.skyblock.events.Events.islandXpMultiplier());
        final int cur = islandXp(islandId);
        trees.purchaseIslandXp(islandId, cur + (int) boosted);
    }

    public int islandXp(final int islandId) {
        return trees.getIslandXp(islandId);
    }

    public int islandLevel(final int islandId) {
        final long total = islandXp(islandId);
        final java.util.List<Long> thresholds = plugin.getConfig().getLongList("island-level-xp");
        int lvl = 0;
        for (int i = 0; i < thresholds.size(); i++) {
            if (total >= thresholds.get(i)) {
                lvl = i;
            }
        }
        return lvl;
    }

    /** Whether an island tree node is owned (level >= 1). */
    public boolean hasIslandUpgrade(final int islandId, final String nodeId) {
        return trees.getLevel(String.valueOf(islandId), "island", nodeId) >= 1;
    }

    public int getIslandUpgradeLevel(final int islandId, final String nodeId) {
        return trees.getLevel(String.valueOf(islandId), "island", nodeId);
    }
}
