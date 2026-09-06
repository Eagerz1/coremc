package net.coremc.skyblock.playerdata;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One player's permanent, owned data.
 *
 * <p>This object is the in-memory representation of {@code data/players/<uuid>.yml}.
 * It is intentionally dumb about where it is persisted — {@link PlayerDataManager}
 * owns the file, the safe-save dance and migrations. PlayerData only knows how to
 * (de)serialise itself and expose typed getters/setters.</p>
 *
 * <p>Design rules (per the CoreMC persistent-data spec):</p>
 * <ul>
 *   <li>The UUID is the permanent identifier. The (volatile) username is stored
 *       only as a convenience for admin display and is never used as a key.</li>
 *   <li>Owned cosmetics/keys are appended-only from the player's perspective:
 *       removing a cosmetic from config never removes it from {@code cosmetics}.</li>
 *   <li>Defaults only fill MISSING fields. Existing values are never overwritten.</li>
 *   <li>Every persisted file carries a {@code data_version} so future schema
 *       changes can be migrated instead of reset.</li>
 * </ul>
 */
public final class PlayerData {

    /** Bump when the on-disk schema changes. Migration lives in PlayerDataManager. */
    public static final int CURRENT_DATA_VERSION = 2;

    private final UUID uuid;
    private String name;

    // ---- Currencies -------------------------------------------------------
    private long credits;
    private long skyTokens;
    private double money;

    // ---- Island Core per-player contribution data -------------------------
    // Shared with the island: every member contributes to the same Core.
    private double coreMoney;     // total Core money this player has generated
    private long coreTokens;      // total Sky Tokens this player has generated via Core
    private long coreProgression; // total mob-kill progression this player has generated

    // ---- Cosmetics (owned ids; definition may or may not exist in config) --
    private final Set<String> skins = new LinkedHashSet<>();
    private final Set<String> tags = new LinkedHashSet<>();
    private final Set<String> gradients = new LinkedHashSet<>();
    private final Set<String> sets = new LinkedHashSet<>();
    private final Set<String> companions = new LinkedHashSet<>();
    private final Set<String> cosmetics = new LinkedHashSet<>();

    // ---- Role-bound Set progression (owned set id -> { xp, armour upgrades }) --
    // Keyed by set id. XP is cumulative; armour upgrades are per-piece levels.
    // Persisted under `sets.<setId>` so a config reload never resets it.
    private final java.util.Map<String, SetProgress> setProgress = new java.util.HashMap<>();

    // ---- Per-role activity statistics (role-scoped) ----
    // Keyed by "<role>:<stat>" (e.g. "mining:blocks", "universal:xp"). Cumulative;
    // never reset by a config reload or a role change (stored independently per role).
    private final java.util.Map<String, Long> roleStats = new java.util.HashMap<>();

    // ---- Lootbox keys (keyed by lootbox id, e.g. "beta", "sotw", "summer") --
    private final java.util.Map<String, Integer> keys = new java.util.HashMap<>();

    // ---- Metadata ---------------------------------------------------------
    private String firstJoin;
    private String lastSeen;
    private int dataVersion = CURRENT_DATA_VERSION;

    // ---- Transient (not persisted) ---------------------------------------
    private boolean dirty = false;

    public PlayerData(final UUID uuid, final String name) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
        final String today = LocalDate.now().toString();
        this.firstJoin = today;
        this.lastSeen = today;
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public void name(final String name) { this.name = name == null ? "" : name; }

    public boolean isDirty() { return dirty; }
    public void markSaved() { this.dirty = false; }
    void markDirty() { this.dirty = true; }

    public int dataVersion() { return dataVersion; }

    // ====================================================================
    //  Currencies
    // ====================================================================
    public long credits() { return credits; }
    public void credits(final long v) { credits = Math.max(0, v); markDirty(); }

    public long skyTokens() { return skyTokens; }
    public void skyTokens(final long v) { skyTokens = Math.max(0, v); markDirty(); }

    public double money() { return money; }
    public void money(final double v) { money = Math.max(0.0, v); markDirty(); }

    // ====================================================================
    //  Island Core per-player contribution data
    // ====================================================================
    public double coreMoney() { return coreMoney; }
    public void coreMoney(final double v) { coreMoney = Math.max(0.0, v); markDirty(); }
    public void addCoreMoney(final double v) { if (v <= 0) return; coreMoney += v; markDirty(); }

    public long coreTokens() { return coreTokens; }
    public void coreTokens(final long v) { coreTokens = Math.max(0, v); markDirty(); }
    public void addCoreTokens(final long v) { if (v <= 0) return; coreTokens += v; markDirty(); }

    public long coreProgression() { return coreProgression; }
    public void coreProgression(final long v) { coreProgression = Math.max(0, v); markDirty(); }
    public void addCoreProgression(final long v) { if (v <= 0) return; coreProgression += v; markDirty(); }

    // ====================================================================
    //  Cosmetics (append-only ownership)
    // ====================================================================
    public Set<String> skins() { return new LinkedHashSet<>(skins); }
    public Set<String> tags() { return new LinkedHashSet<>(tags); }
    public Set<String> gradients() { return new LinkedHashSet<>(gradients); }
    public Set<String> sets() { return new LinkedHashSet<>(sets); }
    public Set<String> companions() { return new LinkedHashSet<>(companions); }

    public boolean hasSkin(final String id) { return skins.contains(id); }
    public void addSkin(final String id) { if (skins.add(id)) markDirty(); }
    public boolean removeSkin(final String id) { return removeFrom(skins, id); }

    public boolean hasTag(final String id) { return tags.contains(id); }
    public void addTag(final String id) { if (tags.add(id)) markDirty(); }

    public boolean hasGradient(final String id) { return gradients.contains(id); }
    public void addGradient(final String id) { if (gradients.add(id)) markDirty(); }

    public boolean hasSet(final String id) { return sets.contains(id); }
    public void addSet(final String id) { if (sets.add(id)) markDirty(); }

    // ---- Role-bound Set progression ----
    /** Cumulative Set XP for a set id (0 if unknown). */
    public long getSetXp(final String setId) {
        final SetProgress sp = setProgress.get(setId);
        return sp == null ? 0L : sp.xp;
    }

    /** Add Set XP (never negative). Marks dirty so it is persisted. */
    public void addSetXp(final String setId, final long amount) {
        if (amount <= 0) return;
        setProgress.computeIfAbsent(setId, k -> new SetProgress()).xp += amount;
        markDirty();
    }

    /** Current upgrade level of an armour piece (0 = none). */
    public int getArmourUpgrade(final String setId, final String piece) {
        final SetProgress sp = setProgress.get(setId);
        if (sp == null) return 0;
        return sp.armour.getOrDefault(normalisePiece(piece), 0);
    }

    /** Set an armour-piece upgrade level (foundation for future upgrade system). */
    public void setArmourUpgrade(final String setId, final String piece, final int level) {
        final SetProgress sp = setProgress.computeIfAbsent(setId, k -> new SetProgress());
        sp.armour.put(normalisePiece(piece), Math.max(0, level));
        markDirty();
    }

    /** All set-progression entries (set id -> xp). */
    public java.util.Map<String, Long> allSetXp() {
        final java.util.Map<String, Long> out = new java.util.HashMap<>();
        for (final var e : setProgress.entrySet()) out.put(e.getKey(), e.getValue().xp);
        return out;
    }

    private static String normalisePiece(final String piece) {
        if (piece == null) return "";
        return piece.toLowerCase(java.util.Locale.ROOT);
    }

    /** Mutable per-set progress container. Not persisted directly (serialised by PlayerData). */
    private static final class SetProgress {
        long xp;
        final java.util.Map<String, Integer> armour = new java.util.HashMap<>();
    }

    public boolean hasCompanion(final String id) { return companions.contains(id); }
    public void addCompanion(final String id) { if (companions.add(id)) markDirty(); }

    public boolean hasCosmetic(final String id) { return cosmetics.contains(id); }
    public void addCosmetic(final String id) { if (cosmetics.add(id)) markDirty(); }

    // ---- Per-role activity statistics (role-scoped) ----
    public long getRoleStat(final String key) {
        return roleStats.getOrDefault(key, 0L);
    }

    public void addRoleStat(final String key, final long amount) {
        if (amount <= 0) return;
        roleStats.put(key, roleStats.getOrDefault(key, 0L) + amount);
        markDirty();
    }

    public java.util.Map<String, Long> allRoleStats() {
        return new java.util.HashMap<>(roleStats);
    }

    private boolean removeFrom(final Set<String> set, final String id) {
        if (set.remove(id)) { markDirty(); return true; }
        return false;
    }

    // ====================================================================
    //  Lootbox keys
    // ====================================================================
    public int getKey(final String lootbox) {
        return keys.getOrDefault(normaliseLootbox(lootbox), 0);
    }

    public void setKey(final String lootbox, final int amount) {
        final String k = normaliseLootbox(lootbox);
        if (amount <= 0) {
            if (keys.remove(k) != null) markDirty();
        } else {
            keys.put(k, amount);
            markDirty();
        }
    }

    public void addKey(final String lootbox, final int amount) {
        if (amount <= 0) return;
        final String k = normaliseLootbox(lootbox);
        keys.put(k, getKey(k) + amount);
        markDirty();
    }

    /** Remove up to {@code amount}; returns the amount actually removed. */
    public int removeKey(final String lootbox, final int amount) {
        if (amount <= 0) return 0;
        final String k = normaliseLootbox(lootbox);
        final int have = getKey(k);
        final int taken = Math.min(have, amount);
        if (taken > 0) setKey(k, have - taken);
        return taken;
    }

    public java.util.Map<String, Integer> allKeys() {
        return new java.util.HashMap<>(keys);
    }

    private static String normaliseLootbox(final String lootbox) {
        return lootbox == null ? "" : lootbox.toLowerCase(java.util.Locale.ROOT);
    }

    // ====================================================================
    //  Metadata
    // ====================================================================
    public String firstJoin() { return firstJoin; }
    public String lastSeen() { return lastSeen; }
    public void touchLastSeen() { this.lastSeen = LocalDate.now().toString(); markDirty(); }

    // ====================================================================
    //  Serialisation
    // ====================================================================

    /**
     * Write this player's data into the given config section. Existing lists in
     * {@code config} are NOT cleared first — we only set the keys we own, which
     * preserves any extra fields a future version may have added.
     */
    void serialize(final YamlConfiguration config) {
        config.set("uuid", uuid.toString());
        config.set("name", name);
        config.set("data_version", CURRENT_DATA_VERSION);

        final ConfigurationSection bal = config.createSection("balances");
        bal.set("credits", credits);
        bal.set("skytokens", skyTokens);
        bal.set("money", money);

        final ConfigurationSection core = config.createSection("core");
        core.set("money", coreMoney);
        core.set("tokens", coreTokens);
        core.set("progression", coreProgression);

        config.set("cosmetics.skins", new ArrayList<>(skins));
        config.set("cosmetics.tags", new ArrayList<>(tags));
        config.set("cosmetics.gradients", new ArrayList<>(gradients));
        config.set("cosmetics.sets", new ArrayList<>(sets));
        config.set("cosmetics.companions", new ArrayList<>(companions));
        config.set("cosmetics.cosmetics", new ArrayList<>(cosmetics));

        // Role-bound Set progression (xp + per-piece armour upgrades).
        final ConfigurationSection setsSec = config.createSection("sets");
        for (final var e : setProgress.entrySet()) {
            final ConfigurationSection s = setsSec.createSection(e.getKey());
            s.set("xp", e.getValue().xp);
            if (!e.getValue().armour.isEmpty()) {
                s.set("armour", new java.util.HashMap<>(e.getValue().armour));
            }
        }

        final ConfigurationSection keysSec = config.createSection("keys");
        for (final var e : keys.entrySet()) {
            if (e.getValue() > 0) keysSec.set(e.getKey(), e.getValue());
        }

        final ConfigurationSection meta = config.createSection("metadata");
        meta.set("first_join", firstJoin);
        meta.set("last_seen", lastSeen);

        // Per-role statistics (role-scoped; cumulative, never reset).
        if (!roleStats.isEmpty()) {
            config.set("role-stats", new java.util.HashMap<>(roleStats));
        }
    }

    /** Build a fresh PlayerData from a loaded config (no migration applied). */
    static PlayerData fromConfig(final YamlConfiguration config, final UUID uuid) {
        final String name = config.getString("name", "");
        final PlayerData data = new PlayerData(uuid, name);

        final ConfigurationSection bal = config.getConfigurationSection("balances");
        if (bal != null) {
            data.credits = Math.max(0, bal.getLong("credits", 0));
            data.skyTokens = Math.max(0, bal.getLong("skytokens", 0));
            data.money = Math.max(0.0, bal.getDouble("money", 0.0));
        }

        final ConfigurationSection core = config.getConfigurationSection("core");
        if (core != null) {
            data.coreMoney = Math.max(0.0, core.getDouble("money", 0.0));
            data.coreTokens = Math.max(0, core.getLong("tokens", 0));
            data.coreProgression = Math.max(0, core.getLong("progression", 0));
        }

        addAll(data.skins, config.getStringList("cosmetics.skins"));
        addAll(data.tags, config.getStringList("cosmetics.tags"));
        addAll(data.gradients, config.getStringList("cosmetics.gradients"));
        addAll(data.sets, config.getStringList("cosmetics.sets"));
        addAll(data.companions, config.getStringList("cosmetics.companions"));
        addAll(data.cosmetics, config.getStringList("cosmetics.cosmetics"));

        // Role-bound Set progression.
        final ConfigurationSection setsSec = config.getConfigurationSection("sets");
        if (setsSec != null) {
            for (final String setId : setsSec.getKeys(false)) {
                final ConfigurationSection s = setsSec.getConfigurationSection(setId);
                if (s == null) continue;
                final SetProgress sp = data.setProgress.computeIfAbsent(setId, k -> new SetProgress());
                sp.xp = Math.max(0, s.getLong("xp", 0));
                final ConfigurationSection arm = s.getConfigurationSection("armour");
                if (arm != null) {
                    for (final String piece : arm.getKeys(false)) {
                        sp.armour.put(piece.toLowerCase(java.util.Locale.ROOT), arm.getInt(piece, 0));
                    }
                }
            }
        }

        final ConfigurationSection keysSec = config.getConfigurationSection("keys");
        if (keysSec != null) {
            for (final String k : keysSec.getKeys(false)) {
                final int v = keysSec.getInt(k, 0);
                if (v > 0) data.keys.put(k.toLowerCase(java.util.Locale.ROOT), v);
            }
        }

        final ConfigurationSection meta = config.getConfigurationSection("metadata");
        if (meta != null) {
            // Dates are stored as ISO strings but YAML may re-parse them as java.util.Date
            // on load; normalise back to the canonical YYYY-MM-DD form so the value is
            // preserved exactly (and stays human-readable in the file).
            data.firstJoin = readIsoDate(meta, "first_join", data.firstJoin);
            data.lastSeen = readIsoDate(meta, "last_seen", data.lastSeen);
        }
        final ConfigurationSection rs = config.getConfigurationSection("role-stats");
        if (rs != null) {
            for (final String k : rs.getKeys(false)) {
                data.roleStats.put(k.toLowerCase(java.util.Locale.ROOT), rs.getLong(k, 0L));
            }
        }
        data.dataVersion = config.getInt("data_version", 0);

        // A freshly loaded object is clean until something mutates it.
        data.dirty = false;
        return data;
    }

    /** Apply default values for any MISSING fields without overwriting existing ones. */
    void applyDefaults() {
        // Balances already default to 0 via the getters; nothing to force-overwrite.
        // Ensure every known lootbox key exists (default 0 is implicit in the map).
        if (firstJoin == null || firstJoin.isEmpty()) firstJoin = LocalDate.now().toString();
        if (lastSeen == null || lastSeen.isEmpty()) lastSeen = LocalDate.now().toString();
    }

    private static void addAll(final Set<String> target, final List<String> src) {
        if (src == null) return;
        for (final String s : src) {
            if (s != null && !s.isEmpty()) target.add(s);
        }
    }

    /**
     * Read a date field that was serialised as an ISO {@code YYYY-MM-DD} string.
     * SnakeYAML may load such a value back as a {@link java.util.Date}; this
     * normalises it back to the canonical string form so the stored value is
     * preserved exactly across save/load round-trips.
     */
    private static String readIsoDate(final ConfigurationSection sec, final String key, final String fallback) {
        final Object v = sec.get(key);
        if (v == null) return fallback;
        if (v instanceof java.util.Date d) {
            return java.time.format.DateTimeFormatter.ISO_LOCAL_DATE.format(
                    d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate());
        }
        if (v instanceof Number n) {
            return java.time.LocalDate.ofEpochDay(n.longValue()).toString();
        }
        return v.toString();
    }

    @Override
    public String toString() {
        return "PlayerData{" + uuid + " name=" + name + " credits=" + credits
                + " skyTokens=" + skyTokens + " money=" + money
                + " skins=" + skins.size() + " tags=" + tags.size()
                + " keys=" + keys + "}";
    }
}
