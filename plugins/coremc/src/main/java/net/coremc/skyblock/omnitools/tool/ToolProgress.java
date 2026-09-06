package net.coremc.skyblock.omnitools.tool;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable per-player, per-Omnitool progress for ONE role's tool.
 *
 * <p>A single instance represents one physical Omnitool (one role). It tracks:
 * <ul>
 *   <li><b>toolLevel</b> — earned by using THIS tool; completely separate from the
 *       player's global Role Level. Each Omnitool has its own tool level.</li>
 *   <li><b>toolXp / toolTotalXp</b> — XP earned with this tool and cumulative total
 *       (total is never reduced; used for the curve + lifetime stats).</li>
 *   <li><b>upgrades</b> — levels of the configurable Omnitool upgrades.</li>
 *   <li><b>perks</b> — perk ids unlocked by reaching tool levels (persisted so they
 *       survive a plugin reload).</li>
 *   <li><b>abilities</b> — active-ability ids this tool has unlocked (level-gated).</li>
 *   <li><b>rebirth</b> — permanent rebirth perks (counts + stacked multipliers).</li>
 * </ul>
 *
 * <p>Tool XP uses a configurable, progressively-increasing curve (see
 * {@link ToolManager#xpForLevel}): level N+1 costs more than level N. This replaces
 * the old flat "1000 XP per level" model so progression can be tuned in config.</p>
 */
public final class ToolProgress {

    // Tool XP -> tool level threshold. Uses a configurable curve (ToolManager.xpForLevel).
    private long toolXp;
    private long toolTotalXp;
    private int toolLevel;
    private final Map<String, Integer> upgrades = new HashMap<>();
    // Perk ids unlocked (level-gated). Persisted so a reload does not re-fire them.
    private final List<String> perks = new ArrayList<>();
    // Active ability ids unlocked (level-gated).
    private final List<String> abilities = new ArrayList<>();
    // Rebirth: how many times rebirthed, and accumulated permanent perk stacks.
    private int rebirthCount;
    private final Map<String, Double> rebirthPerks = new HashMap<>();

    public ToolProgress() {
        this.toolXp = 0;
        this.toolTotalXp = 0;
        this.toolLevel = 1;
    }

    public static ToolProgress fromConfig(final ConfigurationSection sec) {
        final ToolProgress p = new ToolProgress();
        if (sec == null) return p;
        p.toolXp = sec.getLong("tool-xp", 0);
        p.toolTotalXp = sec.getLong("tool-total-xp", p.toolXp);
        p.toolLevel = sec.getInt("tool-level", 1);
        final ConfigurationSection up = sec.getConfigurationSection("upgrades");
        if (up != null) {
            for (final String k : up.getKeys(false)) p.upgrades.put(k, up.getInt(k));
        }
        final List<String> pk = sec.getStringList("perks");
        if (pk != null) p.perks.addAll(pk);
        final List<String> ab = sec.getStringList("abilities");
        if (ab != null) p.abilities.addAll(ab);
        p.rebirthCount = sec.getInt("rebirth", 0);
        final ConfigurationSection perks = sec.getConfigurationSection("rebirth-perks");
        if (perks != null) {
            for (final String k : perks.getKeys(false)) p.rebirthPerks.put(k, perks.getDouble(k));
        }
        return p;
    }

    public long toolXp() { return toolXp; }
    public long toolTotalXp() { return toolTotalXp; }
    public int toolLevel() { return toolLevel; }
    public List<String> perks() { return perks; }
    public List<String> abilities() { return abilities; }
    public int rebirthCount() { return rebirthCount; }
    public Map<String, Integer> upgrades() { return upgrades; }
    public Map<String, Double> rebirthPerks() { return rebirthPerks; }

    public boolean hasPerk(final String id) { return perks.contains(id); }
    public boolean hasAbility(final String id) { return abilities.contains(id); }

    /**
     * Add tool XP and recompute tool level against the supplied curve.
     * {@code xpForLevel} maps level -> cumulative XP required (index = level).
     * Returns true if a level-up occurred on this call.
     */
    public boolean addToolXp(final long amount, final List<Long> curve) {
        boolean leveled = false;
        toolXp += amount;
        toolTotalXp += amount;
        // Walk the curve upward (curve is sorted ascending).
        while (toolLevel < curve.size() - 1 && toolTotalXp >= curve.get(toolLevel + 1)) {
            toolLevel++;
            leveled = true;
        }
        // Honour a configured cap beyond the curve's defined levels.
        return leveled;
    }

    public int upgradeLevel(final String upgrade) {
        return upgrades.getOrDefault(upgrade, 0);
    }

    public void raiseUpgrade(final String upgrade, final int by) {
        upgrades.put(upgrade, upgrades.getOrDefault(upgrade, 0) + by);
    }

    public void addPerk(final String id) {
        if (!perks.contains(id)) perks.add(id);
    }

    public void addAbility(final String id) {
        if (!abilities.contains(id)) abilities.add(id);
    }

    public double rebirthPerk(final String perk) {
        return rebirthPerks.getOrDefault(perk, 0.0);
    }

    public void addRebirth(final Map<String, Double> granted) {
        rebirthCount++;
        for (final var e : granted.entrySet()) {
            rebirthPerks.put(e.getKey(), rebirthPerks.getOrDefault(e.getKey(), 0.0) + e.getValue());
        }
    }

    /** Reset upgrade progression (used on rebirth) but KEEP tool level + rebirth perks. */
    public void resetUpgrades() {
        upgrades.clear();
    }
}
