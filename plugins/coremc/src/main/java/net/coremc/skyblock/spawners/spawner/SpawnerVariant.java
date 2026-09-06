package net.coremc.skyblock.spawners.spawner;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One of the four variants of a spawner mob: Normal, Corrupted, Ancient and the
 * highest ("fourth") variant.
 *
 * <p>Variants are progressively more valuable versions of the SAME mob. They differ
 * only through economy and progression, never through combat stats or custom models:</p>
 * <ul>
 *   <li>{@code requirement} – spawner-tier progression needed to unlock this variant.</li>
 *   <li>{@code cost} – variant purchase cost shown in the spawner GUI.</li>
 *   <li>{@code multiplier} – progression credited per kill (Normal 1x, Ancient 3x, ...).</li>
 *   <li>{@code money} – flat player money per kill.</li>
 *   <li>{@code core} – flat island Core contribution per kill.</li>
 *   <li>{@code tokens} – flat Sky Tokens per kill.</li>
 *   <li>{@code xp} – island XP per kill.</li>
 *   <li>{@code drops} – the configurable drop table.</li>
 * </ul>
 */
public final class SpawnerVariant {

    public static final String NORMAL = "normal";
    public static final String UNCOMMON = "uncommon";
    public static final String RARE = "rare";
    public static final String ANCIENT = "ancient";

    private final String id;
    private final String name;
    private final long requirement;
    private final double cost;
    private final int multiplier;
    private final double money;
    private final double core;
    private final double tokens;
    private final long xp;
    private final String benefit;
    private final List<DropEntry> drops;

    public SpawnerVariant(final String id, final String name, final long requirement,
                          final double cost, final int multiplier, final double money,
                          final double core, final double tokens, final long xp,
                          final String benefit, final List<DropEntry> drops) {
        this.id = id;
        this.name = name;
        this.requirement = Math.max(0, requirement);
        this.cost = cost;
        this.multiplier = Math.max(1, multiplier);
        this.money = money;
        this.core = core;
        this.tokens = tokens;
        this.xp = xp;
        this.benefit = benefit == null ? "" : benefit;
        this.drops = drops == null ? new ArrayList<>() : drops;
    }

    public static SpawnerVariant load(final String id, final ConfigurationSection sec) {
        final String name = sec.getString("name", capitalize(id));
        final long requirement = Math.max(0, sec.getLong("requirement", 0));
        final double cost = sec.getDouble("cost", 0);
        final int multiplier = Math.max(1, sec.getInt("multiplier", 1));
        final double money = sec.getDouble("money", 0);
        final double core = sec.getDouble("core", 0);
        final double tokens = sec.getDouble("tokens", 0);
        final long xp = sec.getLong("xp", 2);
        final String benefit = sec.getString("benefit", "");
        final List<DropEntry> drops = new ArrayList<>();
        final ConfigurationSection dsec = sec.getConfigurationSection("drops");
        if (dsec != null) {
            for (final String key : dsec.getKeys(false)) {
                final ConfigurationSection e = dsec.getConfigurationSection(key);
                if (e != null) {
                    drops.add(DropEntry.load(e));
                }
            }
        }
        return new SpawnerVariant(id, name, requirement, cost, multiplier, money, core, tokens, xp, benefit, drops);
    }

    private static String capitalize(final String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    public String id() { return id; }
    public String name() { return name; }
    public long requirement() { return requirement; }
    public double cost() { return cost; }
    public int multiplier() { return multiplier; }

    /** Alias used by the variant GUI (progression multiplier per kill). */
    public int progressionMultiplier() { return multiplier; }
    public double money() { return money; }
    public double core() { return core; }
    public double tokens() { return tokens; }
    public long xp() { return xp; }
    public String benefit() { return benefit; }
    public List<DropEntry> drops() { return drops; }

    /** A compact "<Name>" label, e.g. "Ancient". */
    public String label() { return name; }

    /** Display name used in GUI / holograms / stack labels. */
    public String displayName() { return name; }

    /**
     * The progression credited per kill for THIS variant. This is the variant's
     * multiplier (e.g. 1 for Normal, 3 for Ancient) — the ACTUAL kill count always
     * stays 1, but progression is multiplied so higher variants accelerate unlocks.
     */
    public long progressionPerKill() { return multiplier; }

    /** Flat island Core money contributed per kill (before drop-derived values). */
    public double coreMoney() { return core; }

    /** Flat Sky Tokens contributed per kill (before drop-derived values). */
    public double coreTokens() { return tokens; }

    /** Flat total sell value of this variant's drops baseline (used by /core debug). */
    public double sell() { return money; }

    /**
     * The vanilla entity this variant spawns. Per the CoreMC spec ALL variants of a
     * mob use the SAME vanilla entity (no custom mob models, no extra AI) — a variant
     * only changes drops / economy / progression, never the creature itself. The
     * {@code mob} argument is the base mob type from the spawner definition and is
     * always returned as-is.
     */
    public EntityType effectiveMob(final EntityType base) { return base; }
}
