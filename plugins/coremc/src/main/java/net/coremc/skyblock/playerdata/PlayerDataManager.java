package net.coremc.skyblock.playerdata;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The single, authoritative source of truth for permanent player ownership data.
 *
 * <p>Every CoreMC subsystem that cares about a player's balances, owned cosmetics
 * or lootbox keys MUST go through this manager. No other system writes
 * {@code data/players/<uuid>.yml}. This prevents the "economy has its own credits,
 * shop has its own credits" split-brain the spec forbids.</p>
 *
 * <h2>Data separation guarantee</h2>
 * <p>Player data lives exclusively under {@code <dataFolder>/data/players/}. The
 * manager never touches {@code config.yml}, {@code messages.yml},
 * {@code lootboxes/} or {@code menus/}. Those are configuration and may be deleted
 * or replaced at any time by an update without affecting a single player file.</p>
 *
 * <h2>Crash-safe saving</h2>
 * <p>Each save writes to {@code <uuid>.yml.tmp} first, then atomically moves it
 * over {@code <uuid>.yml}. A crash mid-write leaves the real file intact (and a
 * stale {@code .tmp} that is simply overwritten next save). On load, if the real
 * file is missing but a {@code .tmp} exists, the {@code .tmp} is recovered.</p>
 *
 * <h2>Versioned migrations</h2>
 * <p>Each file carries {@code data_version}. When {@link PlayerData#CURRENT_DATA_VERSION}
 * advances, {@link #migrate(PlayerData, int)} upgrades old data in-place instead
 * of deleting it. Defaults only fill MISSING fields — existing values are kept.</p>
 *
 * <h2>Backups</h2>
 * <p>Manual backups ({@code /playerdata backup}) and the optional autosave both
 * snapshot the whole {@code data/players/} tree under
 * {@code backups/players/YYYY-MM-DD/}. Retention is configurable and prunes the
 * oldest daily folders, so updates never produce an unbounded pile of backups.</p>
 */
public final class PlayerDataManager {

    private final JavaPlugin plugin;
    private final Logger log;

    private final File playersDir;
    private final File backupsRoot;

    // uuid -> in-memory player data (loaded lazily / on join)
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    // uuid -> pending offline rename target (old username -> current) handled via name() setter
    private final Map<UUID, File> fileFor = new ConcurrentHashMap<>();

    // --- backup / autosave configuration (read from config.yml) ---
    private boolean backupsEnabled;
    private int backupsKeepDays;
    private boolean autosaveEnabled;
    private long autosaveTicks;
    private int backupHour; // hour-of-day to take the daily backup (0-23)

    private org.bukkit.scheduler.BukkitTask autosaveTask;
    private org.bukkit.scheduler.BukkitTask dailyBackupTask;

    public PlayerDataManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        final File dataFolder = plugin.getDataFolder();
        this.playersDir = new File(dataFolder, "data/players");
        this.backupsRoot = new File(dataFolder, "backups/players");
        this.playersDir.mkdirs();
        this.backupsRoot.mkdirs();
    }

    // ====================================================================
    //  Lifecycle
    // ====================================================================

    public void init() {
        reloadConfig();
        if (autosaveEnabled) {
            autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    plugin, this::saveAllDirty, autosaveTicks, autosaveTicks);
            log.info("[playerdata] Autosave every " + (autosaveTicks / 20) + "s.");
        }
        scheduleDailyBackup();
        log.info("[playerdata] Persistent player data ready (" + playersDir.getAbsolutePath() + ").");
    }

    public void reloadConfig() {
        final var cfg = plugin.getConfig();
        final var sec = cfg.getConfigurationSection("playerdata");
        if (sec == null) {
            // Sensible defaults if the operator has not configured anything.
            this.backupsEnabled = true;
            this.backupsKeepDays = 7;
            this.autosaveEnabled = true;
            this.autosaveTicks = 6000L; // 5 minutes
            this.backupHour = 4;
            return;
        }
        this.backupsEnabled = sec.getBoolean("backups-enabled", true);
        this.backupsKeepDays = Math.max(1, sec.getInt("backups-keep-days", 7));
        this.autosaveEnabled = sec.getBoolean("autosave-enabled", true);
        this.autosaveTicks = Math.max(200L, sec.getLong("autosave-interval-ticks", 6000L));
        this.backupHour = Math.min(23, Math.max(0, sec.getInt("backup-hour", 4)));
    }

    public void shutdown() {
        if (autosaveTask != null) autosaveTask.cancel();
        if (dailyBackupTask != null) dailyBackupTask.cancel();
        saveAll();
        log.info("[playerdata] Flushed all player data on shutdown.");
    }

    // ====================================================================
    //  Access / loading
    // ====================================================================

    /** Get (loading if needed) the player's data. Never returns null. */
    public PlayerData get(final UUID uuid) {
        return cache.computeIfAbsent(uuid, this::load);
    }

    /** Mark a player's data dirty so the autosave persists it soon. */
    public void markDirty(final UUID uuid) {
        get(uuid).markDirty();
    }

    /** Ensure data is loaded for an online player; also refresh their username. */
    public PlayerData loadForJoin(final UUID uuid, final String currentName) {
        final PlayerData data = get(uuid);
        if (currentName != null && !currentName.equals(data.name())) {
            data.name(currentName); // username is volatile; ownership stays on uuid
        }
        data.touchLastSeen();
        return data;
    }

    /** True when the player has an on-disk file (i.e. has joined before). */
    public boolean exists(final UUID uuid) {
        return fileOf(uuid).exists();
    }

    private PlayerData load(final UUID uuid) {
        final File file = fileOf(uuid);
        // Crash recovery: prefer a complete .tmp over a missing/corrupt real file.
        final File tmp = tmpFileOf(uuid);
        File source = file;
        if (!file.exists() && tmp.exists()) {
            log.warning("[playerdata] Recovered " + uuid + " from .tmp after incomplete save.");
            source = tmp;
        }
        if (!source.exists()) {
            // First time: create with defaults. This is a NEW player, not a reset.
            final OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            final String name = op.getName() != null ? op.getName() : "";
            final PlayerData fresh = new PlayerData(uuid, name);
            fresh.applyDefaults();
            return fresh;
        }
        try {
            final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(source);
            PlayerData data = PlayerData.fromConfig(cfg, uuid);
            final int onDisk = cfg.getInt("data_version", 0);
            if (onDisk < PlayerData.CURRENT_DATA_VERSION) {
                data = migrate(data, onDisk);
                data.markDirty(); // persist migrated form
            }
            data.applyDefaults();
            return data;
        } catch (final Exception e) {
            log.log(Level.SEVERE, "[playerdata] Failed to load " + uuid + "; starting fresh (old file left as .corrupt).", e);
            final File corrupt = new File(playersDir, uuid + ".yml.corrupt");
            try { Files.move(file.toPath(), corrupt.toPath(), StandardCopyOption.REPLACE_EXISTING); } catch (final IOException ignored) {}
            final PlayerData fresh = new PlayerData(uuid, Bukkit.getOfflinePlayer(uuid).getName());
            fresh.applyDefaults();
            return fresh;
        }
    }

    // ====================================================================
    //  Saving (crash-safe)
    // ====================================================================

    /** Save one player if dirty. Safe-saves to .tmp then atomic move. */
    public void save(final UUID uuid) {
        final PlayerData data = cache.get(uuid);
        if (data == null) return;
        save(data);
    }

    public void save(final PlayerData data) {
        final File file = fileOf(data.uuid());
        final File tmp = tmpFileOf(data.uuid());
        final YamlConfiguration cfg = new YamlConfiguration();
        data.serialize(cfg);
        try {
            cfg.save(tmp);                       // 1. write to .tmp
            Files.move(tmp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE); // 2. atomic swap over real file
            data.markSaved();
        } catch (final IOException e) {
            log.log(Level.SEVERE, "[playerdata] Could not save " + data.uuid() + ": " + e.getMessage(), e);
        }
    }

    /** Save only the players whose data is dirty. */
    public void saveAllDirty() {
        for (final PlayerData data : cache.values()) {
            if (data.isDirty()) save(data);
        }
    }

    /** Save every cached player regardless of dirty flag (shutdown / manual). */
    public void saveAll() {
        for (final PlayerData data : cache.values()) {
            save(data);
        }
    }

    /** Drop a player from the in-memory cache (call on quit AFTER saving). */
    public void unload(final UUID uuid) {
        save(uuid);
        cache.remove(uuid);
        fileFor.remove(uuid);
    }

    // ====================================================================
    //  Migration
    // ====================================================================

    /**
     * Upgrade data from {@code fromVersion} up to the current version. Each step
     * only ADDS/RENAMES fields — it never clears balances, cosmetics or keys.
     */
    private PlayerData migrate(final PlayerData data, final int fromVersion) {
        int v = fromVersion;
        // Example future step (kept as documentation of the pattern):
        // if (v < 2) { /* add a new currency, defaulting missing to 0 */ v = 2; }
        // if (v < 3) { /* rename a lootbox key id */ v = 3; }
        if (v < PlayerData.CURRENT_DATA_VERSION) {
            // Today there is only version 1; nothing to do but record the new version.
            v = PlayerData.CURRENT_DATA_VERSION;
        }
        return data;
    }

    // ====================================================================
    //  Backups
    // ====================================================================

    /**
     * Snapshot the entire {@code data/players/} directory into
     * {@code backups/players/YYYY-MM-DD/}. Prunes backups older than the
     * configured retention window. No-op if backups are disabled.
     */
    public int backupNow() {
        if (!backupsEnabled) {
            log.info("[playerdata] Backups disabled; skipping.");
            return 0;
        }
        saveAll(); // a backup should capture a clean, consistent state
        final String stamp = LocalDate.now().toString();
        final File dest = new File(backupsRoot, stamp);
        dest.mkdirs();
        int copied = 0;
        final File[] files = playersDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (final File f : files) {
                try {
                    Files.copy(f.toPath(), new File(dest, f.getName()).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                } catch (final IOException e) {
                    log.warning("[playerdata] Backup copy failed for " + f.getName() + ": " + e.getMessage());
                }
            }
        }
        pruneBackups();
        log.info("[playerdata] Backup complete: " + copied + " player files -> " + dest.getAbsolutePath());
        return copied;
    }

    private void pruneBackups() {
        final File[] days = backupsRoot.listFiles(File::isDirectory);
        if (days == null) return;
        final List<File> sorted = new ArrayList<>(List.of(days));
        sorted.sort((a, b) -> a.getName().compareTo(b.getName())); // oldest first
        // Keep at most backupsKeepDays daily folders.
        while (sorted.size() > backupsKeepDays) {
            final File oldest = sorted.remove(0);
            deleteRecursively(oldest);
            log.info("[playerdata] Pruned old backup: " + oldest.getName());
        }
    }

    private void scheduleDailyBackup() {
        if (!backupsEnabled) return;
        // Run shortly after the configured backup hour, then every 24h.
        final long now = System.currentTimeMillis();
        final long next = nextOccurrenceOfHour(backupHour);
        final long delayTicks = Math.max(1, (next - now) / 50);
        final long dayTicks = 20L * 60 * 60 * 24;
        dailyBackupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::backupNow, delayTicks, dayTicks);
    }

    private static long nextOccurrenceOfHour(final int hour) {
        final java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime target = now.withHour(hour).withMinute(0).withSecond(0).withNano(0);
        if (!target.isAfter(now)) target = target.plusDays(1);
        return target.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    // ====================================================================
    //  File helpers
    // ====================================================================
    private File fileOf(final UUID uuid) {
        return fileFor.computeIfAbsent(uuid, u -> new File(playersDir, u + ".yml"));
    }
    private File tmpFileOf(final UUID uuid) {
        return new File(playersDir, uuid + ".yml.tmp");
    }

    private static void deleteRecursively(final File dir) {
        final File[] children = dir.listFiles();
        if (children != null) {
            for (final File c : children) {
                if (c.isDirectory()) deleteRecursively(c); else c.delete();
            }
        }
        dir.delete();
    }

    public File playersDirectory() { return playersDir; }
    public File backupsDirectory() { return backupsRoot; }

    // ====================================================================
    //  PUBLIC API  (the single source of truth other systems use)
    // ====================================================================

    // ---- Credits ----
    public long getCredits(final UUID uuid) { return get(uuid).credits(); }
    public void setCredits(final UUID uuid, final long amount) { get(uuid).credits(amount); }
    public long addCredits(final UUID uuid, final long amount) {
        final PlayerData d = get(uuid); d.credits(d.credits() + amount); return d.credits();
    }
    public long removeCredits(final UUID uuid, final long amount) {
        final PlayerData d = get(uuid);
        final long taken = Math.min(d.credits(), Math.max(0, amount));
        d.credits(d.credits() - taken); return taken;
    }

    // ---- SkyTokens ----
    public long getSkyTokens(final UUID uuid) { return get(uuid).skyTokens(); }
    public void setSkyTokens(final UUID uuid, final long amount) { get(uuid).skyTokens(amount); }
    public long addSkyTokens(final UUID uuid, final long amount) {
        final PlayerData d = get(uuid); d.skyTokens(d.skyTokens() + amount); return d.skyTokens();
    }
    public long removeSkyTokens(final UUID uuid, final long amount) {
        final PlayerData d = get(uuid);
        final long taken = Math.min(d.skyTokens(), Math.max(0, amount));
        d.skyTokens(d.skyTokens() - taken); return taken;
    }

    // ---- Money ----
    public double getMoney(final UUID uuid) { return get(uuid).money(); }
    public void setMoney(final UUID uuid, final double amount) { get(uuid).money(amount); }
    public double addMoney(final UUID uuid, final double amount) {
        final PlayerData d = get(uuid); d.money(d.money() + amount); return d.money();
    }
    public double removeMoney(final UUID uuid, final double amount) {
        final PlayerData d = get(uuid);
        final double taken = Math.min(d.money(), Math.max(0.0, amount));
        d.money(d.money() - taken); return taken;
    }

    // ---- Skins ----
    public boolean hasSkin(final UUID uuid, final String id) { return get(uuid).hasSkin(id); }
    public void addSkin(final UUID uuid, final String id) { get(uuid).addSkin(id); }
    public boolean removeSkin(final UUID uuid, final String id) { return get(uuid).removeSkin(id); }

    // ---- Tags ----
    public boolean hasTag(final UUID uuid, final String id) { return get(uuid).hasTag(id); }
    public void addTag(final UUID uuid, final String id) { get(uuid).addTag(id); }

    // ---- Gradients ----
    public boolean hasGradient(final UUID uuid, final String id) { return get(uuid).hasGradient(id); }
    public void addGradient(final UUID uuid, final String id) { get(uuid).addGradient(id); }

    // ---- Sets ----
    public boolean hasSet(final UUID uuid, final String id) { return get(uuid).hasSet(id); }
    public void addSet(final UUID uuid, final String id) { get(uuid).addSet(id); }

    // ---- Companions ----
    public boolean hasCompanion(final UUID uuid, final String id) { return get(uuid).hasCompanion(id); }
    public void addCompanion(final UUID uuid, final String id) { get(uuid).addCompanion(id); }

    // ---- Generic cosmetic (workshop / milestone grant) ----
    public void addCosmetic(final UUID uuid, final String id) { get(uuid).addCosmetic(id); }

    // ---- Lootbox keys ----
    public int getKey(final UUID uuid, final String lootbox) { return get(uuid).getKey(lootbox); }
    public void setKey(final UUID uuid, final String lootbox, final int amount) { get(uuid).setKey(lootbox, amount); }
    public void addKey(final UUID uuid, final String lootbox, final int amount) { get(uuid).addKey(lootbox, amount); }
    public int removeKey(final UUID uuid, final String lootbox, final int amount) { return get(uuid).removeKey(lootbox, amount); }

    // ====================================================================
    //  Bounded in-memory transaction ledger (optional audit view)
    //  Kept in memory only — the authoritative balances live on disk in
    //  data/players/<uuid>.yml. This is a convenience log, not a second store,
    //  so it does not add disk writes on every transaction.
    // ====================================================================
    private static final int LEDGER_MAX = 50;
    private final Map<UUID, java.util.concurrent.ConcurrentLinkedDeque<String>> ledger =
            new ConcurrentHashMap<>();

    private void logTx(final UUID uuid, final String entry) {
        final var q = ledger.computeIfAbsent(uuid, k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        q.addLast(entry);
        while (q.size() > LEDGER_MAX) q.pollFirst();
    }

    /** Last {@code limit} transactions for a player (newest first), in-memory only. */
    public List<String> recentTransactions(final UUID uuid, final int limit) {
        final var q = ledger.get(uuid);
        if (q == null) return new ArrayList<>();
        final List<String> rev = new ArrayList<>();
        for (final var it = q.descendingIterator(); it.hasNext() && rev.size() < limit; ) {
            rev.add(it.next());
        }
        return rev;
    }

    // Overloaded currency helpers that also record a ledger entry.
    public long addCredits(final UUID uuid, final long amount, final String reason, final String executor) {
        final long r = addCredits(uuid, amount);
        logTx(uuid, "credits +" + amount + " by " + executor + " — " + (reason == null ? "" : reason));
        return r;
    }
    public long addSkyTokens(final UUID uuid, final long amount, final String reason, final String executor) {
        final long r = addSkyTokens(uuid, amount);
        logTx(uuid, "skytokens +" + amount + " by " + executor + " — " + (reason == null ? "" : reason));
        return r;
    }
    public long removeSkyTokens(final UUID uuid, final long amount, final String reason, final String executor) {
        final long taken = removeSkyTokens(uuid, amount);
        if (taken > 0) logTx(uuid, "skytokens -" + taken + " by " + executor + " — " + (reason == null ? "" : reason));
        return taken;
    }
    public double addMoney(final UUID uuid, final double amount, final String reason, final String executor) {
        final double r = addMoney(uuid, amount);
        logTx(uuid, "money +" + amount + " by " + executor + " — " + (reason == null ? "" : reason));
        return r;
    }
    public double removeMoney(final UUID uuid, final double amount, final String reason, final String executor) {
        final double taken = removeMoney(uuid, amount);
        if (taken > 0) logTx(uuid, "money -" + taken + " by " + executor + " — " + (reason == null ? "" : reason));
        return taken;
    }
}
