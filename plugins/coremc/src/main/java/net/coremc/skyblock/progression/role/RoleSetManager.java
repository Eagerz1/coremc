package net.coremc.skyblock.progression.role;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.skyblock.playerdata.PlayerData;
import net.coremc.skyblock.playerdata.PlayerDataManager;
import net.coremc.skyblock.progression.api.RoleXpEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Role-bound Set progression system.
 *
 * <p>A player's Set is bound to the Role they choose — selecting a Role permanently
 * grants that Role's Set (idempotent; never repeats, never requires a claim or a
 * purchase). Sets then grow through normal gameplay via Set XP, which is awarded
 * by the existing activity listeners through {@link #addXp(Player, String, long)}.</p>
 *
 * <p>All Set ownership, level, XP and per-piece armour-upgrade state lives in the
 * permanent player data store ({@code data/players/<uuid>.yml}) — never in
 * {@code config.yml} or the lootbox files. A plugin configuration reload NEVER
 * resets a player's set level, XP or armour upgrades.</p>
 *
 * <p>Everything tunable is config-driven:</p>
 * <ul>
 *   <li>{@code progression-armour.role-set-map.<ROLE>} — which Set a Role grants.</li>
 *   <li>{@code set-xp-rates.<activity>} — Set XP per relevant action.</li>
 *   <li>{@code set-level-xp} — cumulative XP to reach each level (increasing).</li>
 *   <li>{@code progression-armour.sets.<id>} — display metadata (name/colour/type/abilities).</li>
 *   <li>{@code progression-armour.sets.<id>.armour-upgrades.<piece>} — the
 *       foundation for individual armour-piece upgrades (requirements scaffolded,
 *       to be expanded with boss kills / quests / special materials later).</li>
 * </ul>
 *
 * <p>No Set XP values, level thresholds or upgrade requirements are hard-coded
 * into the logic.</p>
 */
public final class RoleSetManager {

    private final JavaPlugin plugin;
    private final PlayerDataManager pdm;

    public RoleSetManager(final JavaPlugin plugin, final PlayerDataManager pdm) {
        this.plugin = plugin;
        this.pdm = pdm;
    }

    // ====================================================================
    //  Role -> Set mapping (configurable)
    // ====================================================================

    /** The Set id bound to a Role, or null if none configured. */
    public String setForRole(final RoleManager.Role role) {
        return plugin.getConfig().getString("progression-armour.role-set-map." + role.name(), null);
    }

    /** Permanent Role-set grant. Idempotent — safe to call on every role select. */
    public void grantRoleSet(final Player p, final RoleManager.Role role) {
        final String setId = setForRole(role);
        if (setId == null || setId.isEmpty()) {
            return;
        }
        final PlayerData d = pdm.get(p.getUniqueId());
        if (d.hasSet(setId)) {
            return; // already owns — permanent, never re-grant
        }
        d.addSet(setId);
        pdm.save(p.getUniqueId());
        p.sendMessage(CoreFoundation.getInstance().messages().parse(
                "<prefix> <" + colour(setId) + ">You received the " + displayName(setId)
                        + " Set!</" + colour(setId) + ">",
                plugin.getConfig().getString("prefix")));
    }

    // ====================================================================
    //  Set XP / levels (configurable thresholds)
    // ====================================================================

    /** Cumulative XP required to REACH each level. Index = level. Increasing. */
    private List<Long> levelXp() {
        final List<Long> list = plugin.getConfig().getLongList("set-level-xp");
        if (list.isEmpty()) {
            // Safe default: gentle ramp. Operator should configure this.
            return List.of(0L, 100L, 250L, 500L, 1000L, 2000L, 4000L, 8000L, 16000L, 32000L);
        }
        return list;
    }

    public boolean owns(final UUID uuid, final String setId) {
        return pdm.get(uuid).hasSet(setId);
    }

    public int getLevel(final UUID uuid, final String setId) {
        final long xp = getXp(uuid, setId);
        final List<Long> t = levelXp();
        int lvl = 0;
        for (int i = 0; i < t.size(); i++) {
            if (xp >= t.get(i)) lvl = i;
        }
        return lvl;
    }

    public long getXp(final UUID uuid, final String setId) {
        final PlayerData d = pdm.get(uuid);
        return d.getSetXp(setId);
    }

    /** XP required to reach the NEXT level from current state, or -1 if maxed. */
    public long xpToNext(final UUID uuid, final String setId) {
        final long xp = getXp(uuid, setId);
        final List<Long> t = levelXp();
        for (final long threshold : t) {
            if (xp < threshold) return threshold - xp;
        }
        return -1;
    }

    /** Total XP needed for the next level (cumulative threshold), or -1 if maxed. */
    public long nextLevelThreshold(final UUID uuid, final String setId) {
        final long xp = getXp(uuid, setId);
        final List<Long> t = levelXp();
        for (final long threshold : t) {
            if (xp < threshold) return threshold;
        }
        return -1;
    }

    public int maxLevel() {
        return levelXp().size() - 1;
    }

    /**
     * Award Set XP. The amount comes from {@code set-xp-rates.<activity>} in config
     * (do NOT hardcode progression values). Returns the new level if a level-up
     * occurred, otherwise the current level.
     */
    public int addXp(final Player p, final String activity, final long amount) {
        if (amount <= 0) return currentLevel(p);
        final RoleManager.Role role = CoreMC.getInstance().progression().roles().get(p.getUniqueId());
        if (role == null) return currentLevel(p);
        final String setId = setForRole(role);
        if (setId == null || setId.isEmpty() || !owns(p.getUniqueId(), setId)) {
            return currentLevel(p); // no set yet -> nothing to progress
        }

        final int before = getLevel(p.getUniqueId(), setId);
        final PlayerData d = pdm.get(p.getUniqueId());
        d.addSetXp(setId, amount);
        pdm.save(p.getUniqueId());
        final int after = getLevel(p.getUniqueId(), setId);

        if (after > before) {
            onLevelUp(p, setId, after);
        }
        return after;
    }

    private int currentLevel(final Player p) {
        final RoleManager.Role role = CoreMC.getInstance().progression().roles().get(p.getUniqueId());
        if (role == null) return 0;
        final String setId = setForRole(role);
        return setId == null ? 0 : getLevel(p.getUniqueId(), setId);
    }

    // ====================================================================
    //  Level-up feedback (sound + particle + clean message; no XP spam)
    // ====================================================================

    private void onLevelUp(final Player p, final String setId, final int level) {
        // Clean level-up message (only on actual level-up, never per-XP).
        p.sendMessage(CoreFoundation.getInstance().messages().parse(
                "<prefix> <bold><gold>SET LEVEL UP!</gold></bold>", plugin.getConfig().getString("prefix")));
        p.sendMessage(CoreFoundation.getInstance().messages().parse(
                "<prefix> <" + colour(setId) + ">Your " + displayName(setId) + " Set is now Level "
                        + level + "!</" + colour(setId) + ">",
                plugin.getConfig().getString("prefix")));

        // Level-up sound.
        try {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        } catch (final IllegalArgumentException ignored) {}

        // Small particle effect.
        try {
            p.getWorld().spawnParticle(org.bukkit.Particle.END_ROD,
                    p.getLocation().add(0, 1.2, 0), 18, 0.4, 0.6, 0.4, 0.05);
        } catch (final IllegalArgumentException ignored) {}
    }

    // ====================================================================
    //  Display data (for /sets GUI)
    // ====================================================================

    public String displayName(final String setId) {
        return plugin.getConfig().getString("progression-armour.sets." + setId + ".name", setId);
    }

    public String colour(final String setId) {
        return plugin.getConfig().getString("progression-armour.sets." + setId + ".colour", "white");
    }

    public String type(final String setId) {
        return plugin.getConfig().getString("progression-armour.sets." + setId + ".type", "");
    }

    public List<String> abilities(final String setId) {
        return plugin.getConfig().getStringList("progression-armour.sets." + setId + ".abilities");
    }

    /** Bonuses shown at the current level (foundation; expanded with real perks later). */
    public List<String> currentBonuses(final UUID uuid, final String setId) {
        final List<String> out = new ArrayList<>();
        final int lvl = getLevel(uuid, setId);
        for (final String ab : abilities(setId)) {
            out.add(ab + " <gray>(Lv " + lvl + ")</gray>");
        }
        if (out.isEmpty()) {
            out.add("<gray>No bonuses yet.</gray>");
        }
        return out;
    }

    /** Armour-piece upgrade info for the GUI (foundation for future requirements). */
    public int getArmourUpgrade(final UUID uuid, final String setId, final String piece) {
        return pdm.get(uuid).getArmourUpgrade(setId, piece);
    }

    /** The configured upgrade tiers for a piece (requirements scaffolded for later). */
    public List<ConfigurationSection> armourUpgradeTiers(final String setId, final String piece) {
        final ConfigurationSection sec = plugin.getConfig()
                .getConfigurationSection("progression-armour.sets." + setId + ".armour-upgrades." + piece);
        final List<ConfigurationSection> tiers = new ArrayList<>();
        if (sec == null) return tiers;
        for (final String key : sec.getKeys(false)) {
            final ConfigurationSection t = sec.getConfigurationSection(key);
            if (t != null) tiers.add(t);
        }
        return tiers;
    }

    public List<String> armourPieces() {
        return List.of("helmet", "chestplate", "leggings", "boots");
    }

    /** Build a progress-bar + numbers string for display. */
    public String progressBar(final UUID uuid, final String setId) {
        final long xp = getXp(uuid, setId);
        final long need = nextLevelThreshold(uuid, setId);
        final int filled;
        final int total = 14;
        if (need < 0) {
            filled = total;
        } else {
            final long base = need == 0 ? 0 : need - xpToNext(uuid, setId); // previous threshold
            final double pct = need == 0 ? 1.0 : (double) (xp - base) / (need - base);
            filled = (int) Math.round(Math.max(0, Math.min(1, pct)) * total);
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < total; i++) sb.append(i < filled ? "█" : "░");
        return sb.toString();
    }

    public String formatProgress(final UUID uuid, final String setId) {
        final long xp = getXp(uuid, setId);
        final long need = nextLevelThreshold(uuid, setId);
        if (need < 0) return String.format(Locale.ROOT, "%,d / MAX", xp);
        return String.format(Locale.ROOT, "%,d / %,d XP", xp, need);
    }

    public int progressPercent(final UUID uuid, final String setId) {
        final long xp = getXp(uuid, setId);
        final long need = nextLevelThreshold(uuid, setId);
        if (need < 0) return 100;
        final long base = need == 0 ? 0 : need - xpToNext(uuid, setId);
        final double pct = need == 0 ? 1.0 : (double) (xp - base) / (need - base);
        return (int) Math.round(Math.max(0, Math.min(1, pct)) * 100);
    }

    // ====================================================================
    //  Set XP action helper (called by gameplay listeners / other modules)
    // ====================================================================

    /** Award Set XP for a named activity using the configured rate. */
    public void awardActivityXp(final Player p, final String activity) {
        final long rate = plugin.getConfig().getLong("set-xp-rates." + activity, 0);
        if (rate <= 0) return;
        addXp(p, activity, rate);
    }

    /** Award an explicit Set XP amount for a named activity (e.g. objective completions). */
    public void awardActivityXp(final Player p, final String activity, final long amount) {
        if (amount <= 0) return;
        addXp(p, activity, amount);
    }
}
