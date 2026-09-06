package net.coremc.skyblock.events;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central live-events engine.
 *
 * <p>Owns the active event, the repeating 4-hour schedule, the rotation, the
 * per-category multipliers and the boss bar. Existing reward systems query this
 * manager (never the schedule) when computing awards, so the doubling logic lives
 * in exactly one place.</p>
 *
 * <h2>Schedule model</h2>
 * Every {@code interval} seconds an event starts and runs for {@code duration}
 * seconds; the remaining {@code interval - duration} is downtime. Both are
 * configurable. The schedule is anchored to the wall clock so restarts resume the
 * correct phase instead of resetting.</p>
 *
 * <h2>Persistence</h2>
 * The current phase, the active event id and the absolute start timestamp are
 * written to {@code data/events-state.yml}. On enable we recompute where we are in
 * the cycle from those timestamps, so an event interrupted by a restart resumes with
 * the correct remaining time and the boss bar returns intact.</p>
 */
public final class EventManager {

    private final JavaPlugin plugin;

    /** Package-private accessor for listeners/commands that need the plugin. */
    JavaPlugin plugin() { return plugin; }

    // ---- configuration (reloaded from config.yml) ----
    private boolean enabled;
    private int intervalSeconds;   // full cycle (event + downtime), default 14400 (4h)
    private int durationSeconds;   // event length, default 3600 (1h)
    private final List<String> rotation = new ArrayList<>();
    private final Map<String, EventDefinition> definitions = new LinkedHashMap<>();

    // ---- live state ----
    private String activeId;       // null when in downtime
    private long cycleStartEpoch;  // epoch millis when the current cycle began (event OR downtime)
    private long activeStartEpoch; // epoch millis the active event began (0 when in downtime)
    private int rotationIndex;     // index into rotation of the NEXT event to start

    // ---- boss bar ----
    private BossBar bossBar;
    private final Map<UUID, Boolean> viewers = new ConcurrentHashMap<>();

    // ---- ticking ----
    private org.bukkit.scheduler.BukkitTask ticker;
    // Pending scheduled transition (event end or downtime->event). Cancelled on force start/stop.
    private org.bukkit.scheduler.BukkitTask pendingTransition;

    private static final String STATE_FILE = "events-state.yml";
    private static final int SECTIONS = 4;

    public EventManager(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ============================================================
    //  Lifecycle
    // ============================================================

    public void load() {
        reloadConfig();
        loadState();
    }

    /** Re-read config + definitions, then resume the schedule (or start a fresh cycle). */
    public void reloadConfig() {
        final var cfg = plugin.getConfig();
        final var sec = cfg.getConfigurationSection("events");
        if (sec == null) {
            enabled = false;
            return;
        }
        enabled = sec.getBoolean("enabled", true);
        intervalSeconds = sec.getInt("interval", 14400);
        durationSeconds = sec.getInt("duration", 3600);
        if (intervalSeconds <= durationSeconds) {
            // Guard: event must be shorter than the cycle or we never get downtime.
            intervalSeconds = durationSeconds + 1;
        }

        rotation.clear();
        definitions.clear();

        final List<String> rot = sec.getStringList("rotation");
        final var defs = sec.getConfigurationSection("definitions");
        if (defs != null) {
            for (final String id : defs.getKeys(false)) {
                definitions.put(id, EventDefinition.fromConfig(id, defs.getConfigurationSection(id)));
            }
        }
        // Rotation entries reference definition ids; ignore any without a definition.
        for (final String id : rot) {
            if (definitions.containsKey(id)) rotation.add(id);
        }
        if (rotation.isEmpty() && !definitions.isEmpty()) {
            rotation.addAll(definitions.keySet());
        }
    }

    /** Start ticking. Called after config + state are loaded. */
    public void start() {
        if (!enabled) {
            plugin.getLogger().info("[Events] disabled in config.");
            return;
        }
        if (rotation.isEmpty()) {
            plugin.getLogger().warning("[Events] no rotation configured; events inactive.");
            return;
        }
        resumeCycle();
        // 1 Hz tick: drives the boss bar drain + phase transitions.
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        // Listener so joining players see an in-progress boss bar.
        Bukkit.getPluginManager().registerEvents(new EventsJoinListener(this), plugin);
        // Commands: /events (players) and /event (admin management).
        registerCommands();
        // Live PlaceholderAPI placeholders (%coremc_event%, %coremc_event_time%, ...).
        registerPlaceholders();
        plugin.getLogger().info("[Events] schedule running. interval=" + intervalSeconds
                + "s duration=" + durationSeconds + "s rotation=" + rotation);
    }

    public void shutdown() {
        if (ticker != null) ticker.cancel();
        if (pendingTransition != null) pendingTransition.cancel();
        saveState();
        removeBossBarFromAll();
        if (bossBar != null) bossBar = null;
    }

    // ============================================================
    //  Cycle / scheduling
    // ============================================================

    private long now() { return System.currentTimeMillis(); }

    /** Decide whether we are mid-event or in downtime based on persisted timestamps. */
    private void resumeCycle() {
        final long elapsed = (now() - cycleStartEpoch) / 1000L;
        if (elapsed < 0) {
            // Clock went backwards or state is stale — start a fresh cycle.
            beginCycle(true);
            return;
        }
        if (elapsed < intervalSeconds) {
            // We are inside the current cycle.
            if (elapsed < durationSeconds) {
                // Mid-event: resume it WITHOUT re-announcing (server may have restarted).
                final String id = activeId != null ? activeId : nextEventId();
                beginEvent(id, cycleStartEpoch, false);
            } else {
                // In downtime: keep downtime, schedule the next event at cycle end.
                enterDowntime(cycleStartEpoch);
            }
        } else {
            // Cycle fully elapsed during downtime — start the next one now.
            beginCycle(true);
        }
    }

    /** Start a brand new cycle at {@code now} (optionally begin its event immediately). */
    private void beginCycle(final boolean startEventNow) {
        cycleStartEpoch = now();
        activeId = null;
        activeStartEpoch = 0;
        if (startEventNow) {
            beginEvent(nextEventId(), cycleStartEpoch, true);
        } else {
            enterDowntime(cycleStartEpoch);
        }
        saveState();
    }

    /** Begin downtime for the cycle that started at {@code cycleEpoch}; schedule the next event. */
    private void enterDowntime(final long cycleEpoch) {
        activeId = null;
        activeStartEpoch = 0;
        removeBossBarFromAll();
        final long remaining = Math.max(0, intervalSeconds - ((now() - cycleEpoch) / 1000L));
        if (pendingTransition != null) pendingTransition.cancel();
        pendingTransition = Bukkit.getScheduler().runTaskLater(plugin, () -> beginCycle(true), Math.max(1, remaining) * 20L);
    }

    /** Activate {@code id} as the current event, starting at {@code startEpoch}.
     *  @param announce true to broadcast the start banner + sound (genuine starts);
     *                  false to silently resume the boss bar (e.g. after a restart). */
    private void beginEvent(final String id, final long startEpoch, final boolean announce) {
        activeId = id;
        activeStartEpoch = startEpoch;
        cycleStartEpoch = startEpoch;
        final EventDefinition def = definitions.get(id);
        if (def == null) { activeId = null; return; }
        if (announce) announceStart(def);
        buildBossBar(def);
        // Advance the rotation pointer so the NEXT event is different.
        advanceRotation();
        // Schedule the end of this event.
        final long remain = Math.max(1, durationSeconds - ((now() - startEpoch) / 1000L));
        if (pendingTransition != null) pendingTransition.cancel();
        pendingTransition = Bukkit.getScheduler().runTaskLater(plugin, this::endEvent, remain * 20L);
        saveState();
    }

    /** End the active event, announce, remove boss bar, then re-enter the cycle (downtime). */
    private void endEvent() {
        final EventDefinition def = activeId != null ? definitions.get(activeId) : null;
        removeBossBarFromAll();
        if (def != null) announceEnd(def);
        activeId = null;
        activeStartEpoch = 0;
        // Begin downtime portion of a fresh cycle starting now.
        cycleStartEpoch = now();
        enterDowntime(cycleStartEpoch);
        saveState();
    }

    /** 1 Hz update: refresh boss bar progress + colour + remaining time. */
    private void tick() {
        if (activeId == null || bossBar == null) return;
        final EventDefinition def = definitions.get(activeId);
        if (def == null) return;
        final long elapsed = (now() - activeStartEpoch) / 1000L;
        final long remain = Math.max(0, durationSeconds - elapsed);
        final double progress = Math.max(0.0, Math.min(1.0, 1.0 - (double) elapsed / durationSeconds));
        bossBar.progress((float) progress);

        // Section colour (1..4), one per 15-minute quarter of the event.
        final int section = Math.min(SECTIONS, (int) (elapsed / (durationSeconds / (double) SECTIONS)) + 1);
        bossBar.color(def.barColor(section));

        // Final section emphasis: pulse the title colour a touch brighter is handled in text.
        bossBar.name(bossBarTitle(def, remain, section));
    }

    // ============================================================
    //  Boss bar
    // ============================================================

    private void buildBossBar(final EventDefinition def) {
        if (bossBar != null) bossBar = null;
        bossBar = BossBar.bossBar(
                bossBarTitle(def, durationSeconds, 1),
                1.0f,
                def.barColor(1),
                BossBar.Overlay.PROGRESS);
        for (final Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bossBar);
            viewers.put(p.getUniqueId(), Boolean.TRUE);
        }
    }

    private Component bossBarTitle(final EventDefinition def, final long remainSeconds, final int section) {
        final String mm;
        if (section >= SECTIONS) {
            // Final 15 minutes: emphasise with a brighter accent colour.
            mm = "<bold><" + def.sectionColour(section) + ">" + def.displayName().toUpperCase()
                    + "</" + def.sectionColour(section) + ">\n<yellow>" + formatTime(remainSeconds) + " LEFT</yellow>";
        } else {
            mm = "<bold><" + def.sectionColour(section) + ">" + def.displayName().toUpperCase()
                    + "</" + def.sectionColour(section) + ">\n<white>" + formatTime(remainSeconds) + " LEFT</white>";
        }
        return net.coremc.foundation.util.ColorUtil.parse(mm);
    }

    private void removeBossBarFromAll() {
        if (bossBar == null) return;
        for (final Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bossBar);
        viewers.clear();
    }

    /** Called by the module when a player joins so they see the active bar. */
    public void onJoin(final Player p) {
        if (bossBar != null && activeId != null) p.showBossBar(bossBar);
    }

    public void onQuit(final Player p) {
        viewers.remove(p.getUniqueId());
    }

    // ============================================================
    //  Announcements
    // ============================================================

    private void announceStart(final EventDefinition def) {
        final var msg = CoreFoundation.getInstance().messages();
        final String line = "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>";
        final String body = line + "\n"
                + "<bold><gold>EVENT STARTED</gold></bold>\n\n"
                + "<bold><" + def.sectionColour(1) + ">" + def.displayName().toUpperCase() + "</" + def.sectionColour(1) + "></bold>\n\n"
                + "<green>" + (durationSeconds / 60) + " MINUTES</green>\n" + line;
        msg.broadcast(plugin, body);
        // Server-wide effect — single, non-spammy sound.
        for (final Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
        }
    }

    private void announceEnd(final EventDefinition def) {
        final var msg = CoreFoundation.getInstance().messages();
        final long downtime = Math.max(0, intervalSeconds - durationSeconds);
        final String body = "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>\n"
                + "<bold><red>EVENT ENDED</red></bold>\n\n"
                + "<gray>" + def.displayName() + " has ended.</gray>\n"
                + "<gray>The next event begins in " + (downtime / 60) + " minutes.</gray>\n"
                + "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>";
        msg.broadcast(plugin, body);
    }

    // ============================================================
    //  Multiplier queries (used by existing reward systems)
    // ============================================================

    /** Multiplier for shared island XP (1.0 when no event or wrong type). */
    public double islandXpMultiplier() {
        return multiplierFor(GameEventType.ISLAND_XP);
    }

    /** Multiplier for Slaying Coins (SLAYING role currency). */
    public double slayingCoinsMultiplier() {
        return multiplierFor(GameEventType.SLAYING_COINS);
    }

    /** Multiplier for Omnitool tool XP + role currency (any role). */
    public double omniToolMultiplier() {
        return multiplierFor(GameEventType.OMNITOOL_PROGRESS);
    }

    /** Generic multiplier for a category (used by future event types). */
    public double multiplierFor(final GameEventType type) {
        if (!enabled || activeId == null) return 1.0;
        final EventDefinition def = definitions.get(activeId);
        if (def == null || def.type() != type) return 1.0;
        return def.multiplier();
    }

    // ============================================================
    //  Admin / info API
    // ============================================================

    public boolean isEnabled() { return enabled; }
    public boolean isActive() { return activeId != null; }
    public EventDefinition activeDefinition() { return activeId != null ? definitions.get(activeId) : null; }
    public String activeId() { return activeId; }

    public long remainingSeconds() {
        if (activeId == null) return Math.max(0, intervalSeconds - ((now() - cycleStartEpoch) / 1000L));
        return Math.max(0, durationSeconds - ((now() - activeStartEpoch) / 1000L));
    }

    public EventDefinition nextDefinition() {
        final String id = nextEventId();
        return id != null ? definitions.get(id) : null;
    }

    public long nextInSeconds() {
        if (activeId != null) return remainingSeconds();
        return Math.max(0, intervalSeconds - ((now() - cycleStartEpoch) / 1000L));
    }

    /** The id of the next event that will start (does not mutate rotation pointer). */
    public String nextEventId() {
        if (rotation.isEmpty()) return null;
        return rotation.get(rotationIndex % rotation.size());
    }

    private void advanceRotation() {
        rotationIndex = (rotationIndex + 1) % Math.max(1, rotation.size());
    }

    // ============================================================
    //  Persistence
    // ============================================================

    private File stateFile() {
        return new File(plugin.getDataFolder(), STATE_FILE);
    }

    private void saveState() {
        if (!enabled) return;
        final YamlConfiguration c = new YamlConfiguration();
        c.set("enabled", enabled);
        c.set("cycle-start", cycleStartEpoch);
        c.set("active-id", activeId);
        c.set("active-start", activeStartEpoch);
        c.set("rotation-index", rotationIndex);
        try {
            c.save(stateFile());
        } catch (final java.io.IOException e) {
            plugin.getLogger().warning("[Events] could not save state: " + e.getMessage());
        }
    }

    private void loadState() {
        final File f = stateFile();
        if (!f.exists()) {
            // Fresh start: begin a cycle now (event starts immediately).
            beginCycle(true);
            return;
        }
        final YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
        cycleStartEpoch = c.getLong("cycle-start", now());
        activeId = c.getString("active-id", null);
        activeStartEpoch = c.getLong("active-start", 0);
        rotationIndex = c.getInt("rotation-index", 0);
    }

    // ============================================================
    //  Commands
    // ============================================================

    private void registerCommands() {
        final var eventsCmd = plugin.getCommand("events");
        if (eventsCmd != null) {
            final var exec = new EventsCommand(this);
            eventsCmd.setExecutor(exec);
            eventsCmd.setTabCompleter(exec);
        }
        final var eventCmd = plugin.getCommand("event");
        if (eventCmd != null) {
            final var exec = new EventsCommand(this);
            eventCmd.setExecutor(exec);
            eventCmd.setTabCompleter(exec);
        }
    }

    /**
     * Expose live event state as PlaceholderAPI placeholders under the {@code coremc}
     * expansion (used by TAB scoreboards, GUIs, etc.):
     *   %coremc_event%            active event name, or "None"
     *   %coremc_event_time%       MM:SS left (active) or until next (downtime)
     *   %coremc_event_next%       next event name
     *   %coremc_event_multiplier% x2.0 when active, "—" otherwise
     *   %coremc_event_active%      true / false
     */
    private void registerPlaceholders() {
        net.coremc.foundation.PlaceholderHook.set("event", p -> activeDefinition() == null ? "None" : activeDefinition().displayName());
        net.coremc.foundation.PlaceholderHook.set("event_time", p -> formatTime(remainingSeconds()));
        net.coremc.foundation.PlaceholderHook.set("event_next", p -> {
            final EventDefinition d = nextDefinition();
            return d == null ? "—" : d.displayName();
        });
        net.coremc.foundation.PlaceholderHook.set("event_multiplier", p -> {
            final EventDefinition d = activeDefinition();
            return d == null ? "—" : String.format(java.util.Locale.ROOT, "x%.1f", d.multiplier());
        });
        net.coremc.foundation.PlaceholderHook.set("event_active", p -> Boolean.toString(isActive()));
    }

    // ============================================================
    //  Manual control (admin commands)
    // ============================================================

    /** Force-start a specific (or next) event immediately, ending downtime. */
    public boolean forceStart(final String id) {
        if (!enabled) return false;
        final String target = (id == null || id.isBlank()) ? nextEventId() : id;
        if (target == null || !definitions.containsKey(target)) return false;
        // Cancel any pending scheduled transition (downtime->event or event end) so it
        // does not fire underneath us.
        if (pendingTransition != null) pendingTransition.cancel();
        beginEvent(target, now(), true);
        return true;
    }

    /** Stop the active event now (go to downtime). */
    public void forceStop() {
        if (activeId == null) return;
        if (pendingTransition != null) pendingTransition.cancel();
        final EventDefinition def = definitions.get(activeId);
        removeBossBarFromAll();
        if (def != null) announceEnd(def);
        activeId = null;
        activeStartEpoch = 0;
        cycleStartEpoch = now();
        enterDowntime(cycleStartEpoch);
        saveState();
    }

    // ============================================================
    //  Helpers
    // ============================================================

    public static String formatTime(final long totalSeconds) {
        final long m = totalSeconds / 60;
        final long s = totalSeconds % 60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", m, s);
    }

    public List<EventDefinition> allDefinitions() {
        return new ArrayList<>(definitions.values());
    }
}
