package net.coremc.skyblock.progression.role;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.storage.Island;
import net.coremc.skyblock.events.Events;
import net.coremc.skyblock.progression.ProgressionModule;
import net.coremc.skyblock.progression.api.RoleXpEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Central role-progression orchestrator.
 *
 * <p>This is the single service gameplay listeners and other modules talk to. It owns the
 * per-role XP award, level-up detection, milestone grants, role-stat tracking and the
 * clean integration with the Omnitool system. It does NOT duplicate Omnitool logic — it
 * only calls {@code ToolManager} / {@code RoleMilestone} which route through the existing
 * systems.</p>
 *
 * <p>Award flow (one central point, mirroring the existing {@code ProgressionApi} design):</p>
 * <ol>
 *   <li>Callers invoke {@link #awardActivity} with the player, their role, the activity
 *       category (mining/farming/...) and the base amounts (xp, tokens).</li>
 *   <li>Base values are multiplied by the existing architecture:
 *       <ul>
 *         <li>island buff ({@code ProgressionModule#getBuffMultiplier} for {@code token_mult});</li>
 *         <li>live events ({@code Events#*} multipliers);</li>
 *         <li>role perks ({@code RoleModifiers} — the only new component).</li>
 *       </ul>
 *   </li>
 *   <li>Role XP is credited (firing {@link RoleXpEvent}); on level-up a
 *       {@link RoleLevelUpEvent} is fired and milestones/perks/Omnitool sync run.</li>
 *   <li>Core contribution is routed through the existing {@code CoreService}, again multiplied
 *       by island buffs + events. Role perks add their own (smaller, role-scoped) Core bonus.</li>
 * </ol>
 */
public final class RoleProgression {

    private final ProgressionModule prog;
    private final JavaPlugin plugin;

    private final RoleStats stats;
    private final RoleModifiers modifiers;
    private final RoleMilestone milestones;

    public RoleProgression(final ProgressionModule prog, final JavaPlugin plugin) {
        this.prog = prog;
        this.plugin = plugin;
        this.stats = new RoleStats(plugin);
        this.modifiers = new RoleModifiers(prog);
        this.milestones = new RoleMilestone(prog, plugin);
    }

    public RoleStats stats() { return stats; }
    public RoleModifiers modifiers() { return modifiers; }
    public RoleMilestone milestones() { return milestones; }

    /** Reload config-driven definitions (perks). Milestones are read live from config each call. */
    public void reload() {
        modifiers.reload(plugin);
    }

    // ====================================================================
    //  Central award entry point
    // ====================================================================

    /**
     * Award role progression for a single activity action.
     *
     * @param p        the acting player
     * @param category the activity category (mining/farming/fishing/logging/slaying/universal)
     * @param baseXp   configured base role XP for this action
     * @param baseTokens configured base Sky Tokens for this action
     * @param statKey  which per-role stat to bump by {@code statAmount} (e.g. "blocks")
     * @param statAmount how much to add to that stat
     */
    public void awardActivity(final Player p, final RoleManager.Role role, final String category,
                              final long baseXp, final long baseTokens, final String statKey, final long statAmount) {
        if (role == null) return;

        // 1. multipliers from the existing architecture + role perks.
        final IslandApi api = CoreMC.getInstance().islands().api();
        final Island island = api.getIsland(p.getUniqueId());
        double tokenMult = 1.0;
        if (island != null) tokenMult *= prog.buffs().getMultiplier(island.getId(), "token_mult");
        tokenMult *= Events.roleCurrencyMultiplier(role.name()); // role-scoped event (e.g. slaying coins)
        tokenMult *= modifiers.tokenMultiplier(p.getUniqueId());

        double xpMult = modifiers.xpMultiplier(p.getUniqueId());

        // 2. companion multiplier (existing economy-style hook, applied to tokens).
        final double compMult = CoreMC.getInstance().companions().manager().tokenMultiplier(p);

        final long xpAward = Math.max(1, (long) (baseXp * xpMult));
        final long tokenAward = Math.max(1, (long) (baseTokens * tokenMult * compMult));

        // 3. credit role XP (fires RoleXpEvent; returns whether a level-up occurred).
        awardRoleXp(p, role, xpAward);

        // 4. Sky Tokens via the existing central API.
        prog.api().awardTokens(p.getUniqueId(), tokenAward);

        // 5. track per-role stats (role-scoped, never reset).
        if (statAmount > 0) stats.add(p.getUniqueId(), role, statKey, statAmount);
        stats.add(p.getUniqueId(), role, "xp", xpAward);
        stats.add(p.getUniqueId(), role, "tokens", tokenAward);

        // 6. Role-bound Set XP (existing behaviour, unchanged).
        prog.roleSets().awardActivityXp(p, role.tree());

        // 7. island shared XP (existing behaviour).
        if (island != null) {
            prog.api().awardIslandXp(island.getId(), Math.max(1, (long) (xpAward * 0.5)));
        }

        // 8. Core contribution through the existing Core system, with role-perk Core bonus.
        if (island != null) {
            final double coreMoneyMult = modifiers.coreMoneyMultiplier(p.getUniqueId());
            final double coreTokenMult = modifiers.coreTokenMultiplier(p.getUniqueId());
            final var core = CoreMC.getInstance().islandCore();
            if (core != null) {
                final var cfg = core.service().config();
                final net.coremc.skyblock.islandcore.CoreAction action =
                        net.coremc.skyblock.islandcore.CoreAction.from(category);
                final var av = cfg.actionValues(action);
                core.service().contribute(net.coremc.skyblock.islandcore.CoreContributionContext.builder(
                                island.getId(), p.getUniqueId(), action)
                        .source(category)
                        .baseMoney(av.money * coreMoneyMult)
                        .baseTokens(av.tokens * coreTokenMult)
                        .player(p)
                        .build());
            }
        }
    }

    /** Credit role XP and fire level-up handling if the level increased. */
    public void awardRoleXp(final Player p, final RoleManager.Role role, final long amount) {
        final UUID uuid = p.getUniqueId();
        final int before = prog.roles().getLevel(uuid, role.tree());
        final RoleXpEvent ev = new RoleXpEvent(uuid, role.tree(), amount);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return;
        prog.roles().addXp(uuid, ev.getTree(), ev.getAmount());

        final int after = prog.roles().getLevel(uuid, role.tree());
        if (after > before) {
            onLevelUp(p, role, before, after);
        }
    }

    // ====================================================================
    //  Level-up handling (milestones + perks + Omnitool sync)
    // ====================================================================

    private void onLevelUp(final Player p, final RoleManager.Role role, final int before, final int after) {
        // Fire the extensible level-up event FIRST (quests / future hooks can read it).
        final RoleLevelUpEvent evt = new RoleLevelUpEvent(p, role, before, after);
        Bukkit.getPluginManager().callEvent(evt);

        // Grant every milestone level crossed (idempotent per level).
        for (int lvl = before + 1; lvl <= after; lvl++) {
            if (milestones.entry(role, lvl) != null && milestones.grant(p, role, lvl)) {
                milestones.announce(p, role, lvl);
            }
        }

        // Sync the active Omnitool: role perks can unlock Omnitool upgrades at configured levels.
        syncOmnitool(p, role, after);

        // Clean level-up message (only on actual level-up).
        CoreFoundation.getInstance().messages().sendRaw(p,
                CoreFoundation.getInstance().messages().getPrefix()
                        + " <bold><gold>" + role.display().toUpperCase() + " ROLE LEVEL UP!</gold></bold> <gray>You are now level <white>"
                        + after + "</white>.</gray>");
        try {
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } catch (final IllegalArgumentException ignored) {}
    }

    /** Sync Omnitool perks unlocked at this level (routed through ToolManager, no duplication). */
    private void syncOmnitool(final Player p, final RoleManager.Role role, final int level) {
        // Perk effect "ability" with value ability="omnitool:<upgradeId>" grants a free upgrade
        // level when the perk unlocks. We scan the role's perks for ones that just unlocked.
        for (final RolePerk perk : modifiers.registryForGui().forRole(role.name())) {
            if (!"ability".equals(perk.effect)) continue;
            if (!perk.unlockedAt(level) || perk.unlockLevel != level) continue;
            final String ability = perk.valueString("ability", "");
            if (ability.startsWith("omnitool:")) {
                final String upgrade = ability.substring("omnitool:".length());
                CoreMC.getInstance().omnitools().tools()
                        .grantFreeUpgrade(p.getUniqueId(), role.name(), upgrade, (int) perk.valueDouble("levels", 1));
            }
        }
    }

    /** Convenience: which role category matches a given RoleManager.Role. */
    public static String categoryOf(final RoleManager.Role role) {
        return role.tree();
    }
}
