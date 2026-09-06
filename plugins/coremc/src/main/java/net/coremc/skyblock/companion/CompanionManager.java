package net.coremc.skyblock.companion;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Virtual CoreMC companion system.
 *
 * <p>Companions are owned virtually (counts per {@code (id, rarity)}) and never exist as
 * physical items, so they cannot be dropped, traded or lost. A player may own many but only
 * one is {@link #equipped(UUID) equipped} at a time; only the equipped companion grants buffs
 * and earns XP from its role activity. Ownership, levels, XP and the equipped companion are
 * persisted in {@code companions.yml}.</p>
 *
 * <p>Merging follows: 6 COMMON -&gt; 1 RARE, 4 RARE -&gt; 1 EPIC, 3 EPIC -&gt; 1 MYTHIC. A merged
 * companion starts at Level 0. The XP curve per level is {@code round(xp-base * (level+1)^xp-growth)}
 * from config, so the whole season can be retuned by editing {@code companions.rarity.*}.</p>
 */
public final class CompanionManager {

    /** Activity roles a companion can have. UNIVERSAL companions gain XP from any island activity. */
    public enum Role {
        MINING, FISHING, LOGGING, FARMING, SLAYING, UNIVERSAL
    }

    /** Companion rarities, ordered low to high. */
    public enum Rarity {
        COMMON, RARE, EPIC, MYTHIC;

        public int mergeCost() {
            return switch (this) {
                case COMMON -> 6;
                case RARE -> 4;
                case EPIC -> 3;
                case MYTHIC -> Integer.MAX_VALUE; // cannot merge further
            };
        }

        public Rarity next() {
            return switch (this) {
                case COMMON -> RARE;
                case RARE -> EPIC;
                case EPIC -> MYTHIC;
                case MYTHIC -> null;
            };
        }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    // owned: "<uuid>.<id>.<RARITY>" -> count (number of that companion owned)
    private final ConcurrentHashMap<String, Integer> owned = new ConcurrentHashMap<>();
    // level: "<uuid>.<id>.<RARITY>" -> level
    private final ConcurrentHashMap<String, Integer> level = new ConcurrentHashMap<>();
    // xp: "<uuid>.<id>.<RARITY>" -> xp within current level
    private final ConcurrentHashMap<String, Long> xp = new ConcurrentHashMap<>();
    // equipped: "<uuid>" -> "<id>:<RARITY>"
    private final ConcurrentHashMap<String, String> equipped = new ConcurrentHashMap<>();

    public CompanionManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "companions.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        final ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) return;
        for (final String uk : players.getKeys(false)) {
            final ConfigurationSection pc = players.getConfigurationSection(uk);
            if (pc == null) continue;
            final ConfigurationSection comps = pc.getConfigurationSection("companions");
            if (comps != null) {
                for (final String key : comps.getKeys(false)) {
                    owned.put(uk + "." + key, comps.getInt(key + ".count", 0));
                    level.put(uk + "." + key, comps.getInt(key + ".level", 0));
                    xp.put(uk + "." + key, comps.getLong(key + ".xp", 0));
                }
            }
            final String eq = pc.getString("equipped");
            if (eq != null) equipped.put(uk, eq);
        }
    }

    public void save() {
        try {
            final ConfigurationSection players = data.createSection("players");
            // Rebuild from in-memory maps, grouped by uuid.
            final java.util.Set<String> uuids = new java.util.TreeSet<>();
            for (final String k : owned.keySet()) uuids.add(k.substring(0, k.indexOf('.')));
            for (final String uk : uuids) {
                final ConfigurationSection pc = players.createSection(uk);
                final ConfigurationSection comps = pc.createSection("companions");
                for (final var e : owned.entrySet()) {
                    final String k = e.getKey();
                    if (!k.startsWith(uk + ".") || e.getValue() <= 0) continue;
                    final String sub = k.substring(uk.length() + 1);
                    final ConfigurationSection cs = comps.createSection(sub);
                    cs.set("count", e.getValue());
                    cs.set("level", level.getOrDefault(k, 0));
                    cs.set("xp", xp.getOrDefault(k, 0L));
                }
                final String eq = equipped.get(uk);
                if (eq != null) pc.set("equipped", eq);
            }
            data.save(file);
        } catch (final java.io.IOException e) {
            plugin.getLogger().warning("Could not save companions.yml: " + e.getMessage());
        }
    }

    // ---- config helpers ----

    private String ckey(final String id, final Rarity r) {
        return id + "." + r.name();
    }

    public List<String> companionIds() {
        final ConfigurationSection sec = plugin.getConfig().getConfigurationSection("companions.list");
        return sec == null ? new ArrayList<>() : new ArrayList<>(sec.getKeys(false));
    }

    public boolean exists(final String id) {
        return plugin.getConfig().isConfigurationSection("companions.list." + id);
    }

    public String displayName(final String id) {
        return plugin.getConfig().getString("companions.list." + id + ".name", id);
    }

    public Role role(final String id) {
        final String r = plugin.getConfig().getString("companions.list." + id + ".role", "UNIVERSAL");
        try {
            return Role.valueOf(r.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return Role.UNIVERSAL;
        }
    }

    public String material(final String id) {
        return plugin.getConfig().getString("companions.list." + id + ".material", "PAINTING");
    }

    public List<Rarity> rarities() {
        return java.util.List.of(Rarity.COMMON, Rarity.RARE, Rarity.EPIC, Rarity.MYTHIC);
    }

    public String rarityColour(final Rarity r) {
        return plugin.getConfig().getString("companions.rarity." + r.name() + ".colour", "white");
    }

    public int maxLevel(final Rarity r) {
        return plugin.getConfig().getInt("companions.rarity." + r.name() + ".max-level", 50);
    }

    /** XP required to advance FROM the given level to the next (0 for maxed). */
    public long xpForLevel(final Rarity r, final int lvl) {
        if (lvl >= maxLevel(r)) return 0;
        final double base = plugin.getConfig().getDouble("companions.rarity." + r.name() + ".xp-base", 50);
        final double growth = plugin.getConfig().getDouble("companions.rarity." + r.name() + ".xp-growth", 1.2);
        return Math.round(base * Math.pow(lvl + 1, growth));
    }

    /** Total XP from level 0 to max for a rarity (for "XP to Max" display). */
    public long xpToMax(final Rarity r) {
        long total = 0;
        for (int l = 0; l < maxLevel(r); l++) total += xpForLevel(r, l);
        return total;
    }

    // ---- ownership ----

    public int ownedCount(final UUID uuid, final String id, final Rarity r) {
        return owned.getOrDefault(uuid.toString() + "." + ckey(id, r), 0);
    }

    /** Grant {@code n} companions of (id, rarity). Announces if a player is online. */
    public void grant(final Player p, final String id, final Rarity r, final int n) {
        if (!exists(id)) {
            plugin.getLogger().warning("[companions] unknown companion: " + id);
            return;
        }
        final String key = p.getUniqueId().toString() + "." + ckey(id, r);
        owned.put(key, owned.getOrDefault(key, 0) + n);
        if (!level.containsKey(key)) level.put(key, 0);
        if (!xp.containsKey(key)) xp.put(key, 0L);
        save();
        p.sendMessage(CoreFoundation.getInstance().messages().parse(
                "<prefix> <" + rarityColour(r) + ">You obtained " + n + "x " + displayName(id)
                        + " (" + r.name() + ")!</" + rarityColour(r) + ">",
                plugin.getConfig().getString("prefix")));
    }

    private void removeCount(final UUID uuid, final String id, final Rarity r, final int n) {
        final String key = uuid.toString() + "." + ckey(id, r);
        final int left = Math.max(0, owned.getOrDefault(key, 0) - n);
        if (left <= 0) {
            owned.remove(key);
            level.remove(key);
            xp.remove(key);
        } else {
            owned.put(key, left);
        }
    }

    // ---- equip ----

    public String equipped(final UUID uuid) {
        return equipped.get(uuid.toString());
    }

    public String equipped(final Player p) {
        return equipped(p.getUniqueId());
    }

    /** Returns "id:rarity" of the equipped companion, or null. */
    public String equippedKey(final UUID uuid) {
        return equipped.get(uuid.toString());
    }

    /** Equip a specific owned companion. Returns true if equipped. */
    public boolean equip(final Player p, final String id, final Rarity r) {
        if (ownedCount(p.getUniqueId(), id, r) <= 0) return false;
        equipped.put(p.getUniqueId().toString(), id + ":" + r.name());
        save();
        p.sendMessage(CoreFoundation.getInstance().messages().parse(
                "<prefix> <" + rarityColour(r) + ">Equipped " + displayName(id) + " (" + r.name() + ").</"
                        + rarityColour(r) + ">",
                plugin.getConfig().getString("prefix")));
        return true;
    }

    public boolean unequip(final Player p) {
        if (equipped.remove(p.getUniqueId().toString()) == null) return false;
        save();
        p.sendMessage(CoreFoundation.getInstance().messages().parse(
                "<prefix> <gray>You unequipped your companion.</gray>",
                plugin.getConfig().getString("prefix")));
        return true;
    }

    // ---- XP ----

    /** Award XP to a specific companion instance. Handles level-ups (capped at max). */
    public void addXp(final UUID uuid, final String id, final Rarity r, final long amount) {
        final String key = uuid.toString() + "." + ckey(id, r);
        if (owned.getOrDefault(key, 0) <= 0) return;
        int lvl = level.getOrDefault(key, 0);
        long cur = xp.getOrDefault(key, 0L);
        cur += amount;
        boolean leveled = false;
        while (lvl < maxLevel(r)) {
            final long need = xpForLevel(r, lvl);
            if (cur >= need) {
                cur -= need;
                lvl++;
                leveled = true;
            } else {
                break;
            }
        }
        if (lvl >= maxLevel(r)) cur = 0; // clamp overflow at max
        level.put(key, lvl);
        xp.put(key, cur);
    }

    public int levelOf(final UUID uuid, final String id, final Rarity r) {
        return level.getOrDefault(uuid.toString() + "." + ckey(id, r), 0);
    }

    public long xpOf(final UUID uuid, final String id, final Rarity r) {
        return xp.getOrDefault(uuid.toString() + "." + ckey(id, r), 0L);
    }

    // ---- merging ----

    /**
     * Merge one step for a companion id, consuming the lowest complete tier the player owns.
     * Returns the produced rarity, or null if nothing could be merged.
     */
    public Rarity merge(final Player p, final String id) {
        if (!exists(id)) return null;
        final UUID uuid = p.getUniqueId();
        for (final Rarity r : rarities()) {
            final Rarity next = r.next();
            if (next == null) continue;
            final int cost = r.mergeCost();
            if (ownedCount(uuid, id, r) >= cost) {
                removeCount(uuid, id, r, cost);
                final String nk = uuid.toString() + "." + ckey(id, next);
                owned.put(nk, owned.getOrDefault(nk, 0) + 1);
                level.put(nk, 0);
                xp.put(nk, 0L);
                // If the merged-away tier was equipped, move equip to the new tier.
                final String eq = equipped.get(uuid.toString());
                if ((id + ":" + r.name()).equals(eq)) {
                    equipped.put(uuid.toString(), id + ":" + next.name());
                }
                save();
                p.sendMessage(CoreFoundation.getInstance().messages().parse(
                        "<prefix> <" + rarityColour(next) + ">Merged " + cost + "x " + displayName(id)
                                + " (" + r.name() + ") into 1x " + displayName(id) + " (" + next.name()
                                + ")!</" + rarityColour(next) + ">",
                        plugin.getConfig().getString("prefix")));
                return next;
            }
        }
        return null;
    }

    // ---- buff queries (equipped companion only) ----

    private String equippedId(final UUID uuid) {
        final String k = equipped.get(uuid.toString());
        return k == null ? null : k.substring(0, k.lastIndexOf(':'));
    }

    private Rarity equippedRarity(final UUID uuid) {
        final String k = equipped.get(uuid.toString());
        if (k == null) return null;
        try {
            return Rarity.valueOf(k.substring(k.lastIndexOf(':') + 1));
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    /** Role of the equipped companion, or null. */
    public Role equippedRole(final UUID uuid) {
        final String id = equippedId(uuid);
        return id == null ? null : role(id);
    }

    private double perLevel(final Rarity r, final String node, final double def) {
        return plugin.getConfig().getDouble("companions.rarity." + r.name() + "." + node, def);
    }

    private double cap(final Rarity r, final String node, final double def) {
        return plugin.getConfig().getDouble("companions.rarity." + r.name() + "." + node, def);
    }

    /** Rare Drop Chance (%) for the equipped role companion (0 if none / universal). */
    public double rareDropChance(final Player p) {
        final Rarity r = equippedRarity(p.getUniqueId());
        if (r == null || equippedRole(p.getUniqueId()) == Role.UNIVERSAL) return 0;
        final int lvl = levelOf(p.getUniqueId(), equippedId(p.getUniqueId()), r);
        return Math.min(cap(r, "rare-drop-cap", 25), lvl * perLevel(r, "rare-drop-per-level", 0.05));
    }

    /** Bonus Drop Chance (%) for the equipped role companion (0 if none / universal). */
    public double bonusDropChance(final Player p) {
        final Rarity r = equippedRarity(p.getUniqueId());
        if (r == null || equippedRole(p.getUniqueId()) == Role.UNIVERSAL) return 0;
        final int lvl = levelOf(p.getUniqueId(), equippedId(p.getUniqueId()), r);
        return Math.min(cap(r, "bonus-drop-cap", 20), lvl * perLevel(r, "bonus-drop-per-level", 0.05));
    }

    /** Money multiplier for the equipped UNIVERSAL companion (1.0 if none / role). */
    public double moneyMultiplier(final Player p) {
        final Rarity r = equippedRarity(p.getUniqueId());
        if (r == null || equippedRole(p.getUniqueId()) != Role.UNIVERSAL) return 1.0;
        final int lvl = levelOf(p.getUniqueId(), equippedId(p.getUniqueId()), r);
        return 1.0 + Math.min(cap(r, "money-cap", 1.0), lvl * perLevel(r, "money-per-level", 0.005));
    }

    /** Sky Token multiplier for the equipped UNIVERSAL companion (1.0 if none / role). */
    public double tokenMultiplier(final Player p) {
        final Rarity r = equippedRarity(p.getUniqueId());
        if (r == null || equippedRole(p.getUniqueId()) != Role.UNIVERSAL) return 1.0;
        final int lvl = levelOf(p.getUniqueId(), equippedId(p.getUniqueId()), r);
        return 1.0 + Math.min(cap(r, "token-cap", 1.0), lvl * perLevel(r, "token-per-level", 0.006));
    }

    /** Current buff value lines for a companion instance (for the GUI). */
    public List<String> buffLines(final String id, final Rarity r, final int lvl) {
        final List<String> out = new ArrayList<>();
        if (role(id) == Role.UNIVERSAL) {
            final double m = 1.0 + Math.min(cap(r, "money-cap", 1.0), lvl * perLevel(r, "money-per-level", 0.005));
            final double t = 1.0 + Math.min(cap(r, "token-cap", 1.0), lvl * perLevel(r, "token-per-level", 0.006));
            out.add("<white>• Money Multiplier: +" + Format_pct(m - 1.0) + "%</white>");
            out.add("<white>• Sky Token Multiplier: +" + Format_pct(t - 1.0) + "%</white>");
        } else {
            final double rd = Math.min(cap(r, "rare-drop-cap", 25), lvl * perLevel(r, "rare-drop-per-level", 0.05));
            final double bd = Math.min(cap(r, "bonus-drop-cap", 20), lvl * perLevel(r, "bonus-drop-per-level", 0.05));
            out.add("<white>• Rare Drop Chance: +" + Format_pct(rd) + "%</white>");
            out.add("<white>• Bonus Drop Chance: +" + Format_pct(bd) + "%</white>");
        }
        return out;
    }

    private static String Format_pct(final double pct) {
        return String.format(Locale.ROOT, "%.2f", pct);
    }

    /** XP task description for a companion's role (for the GUI). */
    public String xpTask(final String id) {
        return switch (role(id)) {
            case MINING -> "Mining Blocks";
            case FISHING -> "Catching Fish";
            case LOGGING -> "Breaking Logs";
            case FARMING -> "Harvesting Crops";
            case SLAYING -> "Slaying Mobs";
            case UNIVERSAL -> "Island Activities";
        };
    }
}
