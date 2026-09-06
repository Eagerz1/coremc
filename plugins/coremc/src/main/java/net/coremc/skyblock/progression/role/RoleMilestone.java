package net.coremc.skyblock.progression.role;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.skyblock.progression.ProgressionModule;
import net.coremc.skyblock.progression.token.TokenManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Role milestone framework.
 *
 * <p>Milestones are one-time rewards granted when a player REACHES a configured role level
 * (config section {@code role-milestones.<role>.<level>}). They are separate from the passive
 * {@link RolePerk} framework: perks are continuous multipliers/flags, milestones are discrete
 * grants (Sky Tokens, Sky Tokens, Core contribution, cosmetics, Omnitool upgrade unlocks,
 * messages).</p>
 *
 * <p>Each milestone entry supports:</p>
 * <ul>
 *   <li>{@code tokens}         — Sky Tokens granted once.</li>
 *   <li>{@code credits}        — Credits granted once (uses existing CreditsBridge/CoreMC-Store).</li>
 *   <li>{@code core-money} / {@code core-tokens} / {@code core-progression} — one-time Core contribution.</li>
 *   <li>{@code omnitool-upgrade} (string) — a free level of this Omnitool upgrade (role-scoped),
 *       routed through the existing {@code ToolManager} (no duplicated logic).</li>
 *   <li>{@code cosmetic} / {@code skin} / {@code tag} / {@code gradient} — ownership grant
 *       (uses the existing permanent player-data cosmetics).</li>
 *   <li>{@code message}        — broadcast/announce line shown on unlock.</li>
 *   <li>{@code rewards} (list) — human-readable reward summary shown in the GUI.</li>
 * </ul>
 *
 * <p>Everything is config-driven; adding a new milestone type means handling a new key here
 * once. The grant is idempotent — a player can only receive each milestone level once, tracked
 * by {@link RoleManager#hasClaimedMilestone}.</p>
 */
public final class RoleMilestone {

    private final ProgressionModule prog;
    private final JavaPlugin plugin;

    public RoleMilestone(final ProgressionModule prog, final JavaPlugin plugin) {
        this.prog = prog;
        this.plugin = plugin;
    }

    /** All configured milestone levels for a role (sorted asc). */
    public List<Integer> milestoneLevels(final RoleManager.Role role) {
        final ConfigurationSection sec = plugin.getConfig()
                .getConfigurationSection("role-milestones." + role.name());
        if (sec == null) return List.of();
        final List<Integer> out = new ArrayList<>();
        for (final String k : sec.getKeys(false)) {
            try { out.add(Integer.parseInt(k)); } catch (final NumberFormatException ignored) {}
        }
        out.sort(Integer::compareTo);
        return out;
    }

    /** Next unclaimed milestone level at or above {@code level}, or -1 if none. */
    public int nextMilestone(final RoleManager.Role role, final UUID uuid, final int level) {
        for (final int m : milestoneLevels(role)) {
            if (m > level) return m;
            if (m <= level && !prog.roles().hasClaimedMilestone(uuid, role, m)) return m;
        }
        return -1;
    }

    /** Read a milestone entry, or null if not configured. */
    public ConfigurationSection entry(final RoleManager.Role role, final int level) {
        final ConfigurationSection sec = plugin.getConfig()
                .getConfigurationSection("role-milestones." + role.name() + "." + level);
        return sec;
    }

    public List<String> rewardSummary(final RoleManager.Role role, final int level) {
        final ConfigurationSection sec = entry(role, level);
        if (sec == null) return List.of();
        final List<String> rewards = sec.getStringList("rewards");
        if (!rewards.isEmpty()) return rewards;
        // Fallback: synthesise a short summary from the configured keys.
        final List<String> out = new ArrayList<>();
        if (sec.contains("tokens")) out.add("+" + sec.getLong("tokens") + " Sky Tokens");
        if (sec.contains("credits")) out.add("+" + sec.getLong("credits") + " Credits");
        if (sec.contains("core-money")) out.add("+" + sec.getDouble("core-money") + " Core Money");
        if (sec.contains("core-tokens")) out.add("+" + sec.getLong("core-tokens") + " Core Tokens");
        if (sec.contains("cosmetic")) out.add("Cosmetic: " + sec.getString("cosmetic"));
        if (sec.contains("omnitool-upgrade")) out.add("Omnitool: " + sec.getString("omnitool-upgrade"));
        return out;
    }

    /**
     * Grant a milestone the player just reached. Idempotent — returns true if it was newly
     * granted (and the caller should announce). Does nothing if already claimed.
     */
    public boolean grant(final Player p, final RoleManager.Role role, final int level) {
        if (prog.roles().hasClaimedMilestone(p.getUniqueId(), role, level)) return false;
        final ConfigurationSection sec = entry(role, level);
        if (sec == null) return false;

        prog.roles().claimMilestone(p.getUniqueId(), role, level);

        // ---- currency / core grants via existing systems ----
        if (sec.contains("tokens")) {
            prog.tokens().add(p.getUniqueId(), Math.max(0, sec.getLong("tokens")));
        }
        if (sec.contains("credits")) {
            CoreMC.getInstance().playerDataManager()
                    .addCredits(p.getUniqueId(), Math.max(0, sec.getLong("credits")),
                            "Role milestone " + role.display() + " " + level, "role");
        }
        final double coreMoney = sec.getDouble("core-money", 0);
        final long coreTokens = sec.getLong("core-tokens", 0);
        final long coreProg = sec.getLong("core-progression", 0);
        if (coreMoney > 0 || coreTokens > 0 || coreProg > 0) {
            grantCore(p, role, coreMoney, coreTokens, coreProg);
        }

        // ---- cosmetics via permanent player data ----
        final String cosmetic = sec.getString("cosmetic", null);
        if (cosmetic != null && !cosmetic.isEmpty()) {
            CoreMC.getInstance().playerDataManager().addCosmetic(p.getUniqueId(), cosmetic);
        }
        final String skin = sec.getString("skin", null);
        if (skin != null && !skin.isEmpty()) {
            CoreMC.getInstance().playerDataManager().addSkin(p.getUniqueId(), skin);
        }
        final String tag = sec.getString("tag", null);
        if (tag != null && !tag.isEmpty()) {
            CoreMC.getInstance().playerDataManager().addTag(p.getUniqueId(), tag);
        }
        final String gradient = sec.getString("gradient", null);
        if (gradient != null && !gradient.isEmpty()) {
            CoreMC.getInstance().playerDataManager().addGradient(p.getUniqueId(), gradient);
        }

        // ---- Omnitool upgrade unlock (routed through ToolManager, no duplication) ----
        final String upgrade = sec.getString("omnitool-upgrade", null);
        if (upgrade != null && !upgrade.isEmpty()) {
            CoreMC.getInstance().omnitools().tools().grantFreeUpgrade(p.getUniqueId(), role.name(), upgrade,
                    sec.getInt("omnitool-upgrade-levels", 1));
        }

        return true;
    }

    /** Grant one-time Core contribution through the existing Core service (island-scoped). */
    private void grantCore(final Player p, final RoleManager.Role role,
                           final double money, final long tokens, final long prog2) {
        final var core = CoreMC.getInstance().islandCore();
        if (core == null) return;
        final var island = CoreMC.getInstance().islands().api().getIsland(p.getUniqueId());
        if (island == null) return;
        core.service().contribute(net.coremc.skyblock.islandcore.CoreContributionContext.builder(
                        island.getId(), p.getUniqueId(),
                        net.coremc.skyblock.islandcore.CoreAction.from(role.tree()))
                .source("milestone:" + role.tree() + ":" + role.name())
                .baseMoney((long) money)
                .baseTokens(tokens)
                .kills(prog2)
                .player(p)
                .applyEventMultipliers(false) // milestone grant, not a live event
                .build());
    }

    /** Announce a milestone unlock (message + sound + particles), clean and non-spammy. */
    public void announce(final Player p, final RoleManager.Role role, final int level) {
        final ConfigurationSection sec = entry(role, level);
        String msg = sec == null ? null : sec.getString("message");
        if (msg == null || msg.isEmpty()) {
            msg = "<bold><gold>ROLE MILESTONE!</gold></bold> <" + roleColour(role)
                    + ">" + role.display() + " " + level + "</" + roleColour(role) + "> reached!";
        }
        CoreFoundation.getInstance().messages().sendRaw(p,
                CoreFoundation.getInstance().messages().getPrefix() + " " + msg);
        try {
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        } catch (final IllegalArgumentException ignored) {}
        try {
            p.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1.2, 0),
                    24, 0.5, 0.6, 0.5, 0.08);
        } catch (final IllegalArgumentException ignored) {}
    }

    private static String roleColour(final RoleManager.Role role) {
        return switch (role) {
            case MINING -> "gray";
            case FARMING -> "green";
            case FISHING -> "aqua";
            case LOGGING -> "dark_green";
            case SLAYING -> "dark_red";
            case UNIVERSAL -> "white";
        };
    }
}
