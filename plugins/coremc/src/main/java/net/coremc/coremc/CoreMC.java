package net.coremc.coremc;

import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.messages.Messages;
import net.coremc.foundation.util.FormatUtil;
import net.coremc.foundation.util.ColorUtil;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.api.ProgressionProvider;
import net.coremc.skyblock.core.IslandModule;
import net.coremc.skyblock.crates.CrateModule;
import net.coremc.store.CoreStore;
import net.coremc.skyblock.events.EventManager;
import net.coremc.skyblock.gens.GensModule;
import net.coremc.skyblock.islandcore.IslandCoreModule;
import net.coremc.skyblock.missions.MissionsModule;
import net.coremc.skyblock.playerdata.PlayerData;
import net.coremc.skyblock.playerdata.PlayerDataManager;
import net.coremc.skyblock.progression.ProgressionModule;
import net.coremc.skyblock.progression.role.RoleSetManager;
import net.coremc.skyblock.spawners.SpawnersModule;
import net.coremc.skyblock.omnitools.OmnitoolsModule;
import net.coremc.skyblock.companion.CompanionModule;
import net.coremc.coreban.BanModule;
import net.coremc.corestaff.StaffModule;
import net.coremc.foundation.PlaceholderHook;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

/**
 * Consolidated CoreMC plugin.
 * <p>
 * All CoreMC-owned functionality is handled internally by this single plugin.
 * Former separate plugins (CoreTags, CoreChatColor, CoreBan, etc.) have been
 * merged into clean modules under the CoreMC architecture.
 * <p>
 * Key design principles:
 * - Single player data store (PlayerDataManager) — all subsystems read/write here
 * - Central message system (Messages + CoreFoundation) — consistent formatting
 * - Central PlaceholderAPI expansion — one expansion, all placeholders
 * - No Bukkit PluginManager lookups between CoreMC systems — direct service access
 * - Graceful degradation: if PlaceholderAPI is unavailable, placeholders return safe fallbacks
 */
public final class CoreMC extends JavaPlugin {

    private static CoreMC instance;

    // Shared foundation
    private CoreFoundation foundation;
    private Messages messages;

    // Player data (single source of truth for all player-owned data)
    private PlayerDataManager playerDataManager;

    // Gameplay subsystems — now internal services, no cross-plugin lookups
    private IslandModule islands;
    private ProgressionModule progression;
    private RoleSetManager roleSets;
    private GensModule gens;
    private SpawnersModule spawners;
    private OmnitoolsModule omnitools;
    private BanModule ban;
    private StaffModule staff;
    private CrateModule crates;
    private MissionsModule missions;
    private CompanionModule companions;
    private EventManager events;
    private IslandCoreModule islandCore;
    private CoreStore storeModule;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // --- Shared foundation (messages, GUI, formatting, cooldowns) ---
        this.foundation = new CoreFoundation(this);
        this.messages = foundation.messages();

        // --- Persistent player ownership data — MUST init first so every other
        // subsystem reads/writes the single canonical store (data/players/<uuid>.yml) ---
        this.playerDataManager = new PlayerDataManager(this);
        playerDataManager.init();

        // --- Gameplay subsystems (all now use playerDataManager for shared data) ---
        this.islands = new IslandModule(this);
        islands.init();

        this.progression = new ProgressionModule(this);
        progression.init();

        // Role-bound Set progression (persists in permanent player data).
        this.roleSets = new RoleSetManager(this, playerDataManager);
        this.gens = new GensModule(this);
        this.gens.init();

        this.spawners = new SpawnersModule(this);
        this.spawners.init();

        this.omnitools = new OmnitoolsModule(this);
        this.omnitools.init();

        this.ban = new BanModule(this);
        this.ban.init();

        this.staff = new StaffModule(this);
        this.staff.init();

        this.crates = new CrateModule(this);
        this.crates.init();

        this.missions = new MissionsModule(this);
        this.missions.init();

        this.companions = new CompanionModule(this);
        this.companions.init();

        // Global live events — load config + resume (or start) the schedule.
        this.events = new EventManager(this);
        this.events.load();
        this.events.start();

        // Island Core (shared Core contribution) — foundation for role/mob/activity contributions.
        this.islandCore = new IslandCoreModule(this);
        this.islandCore.init();

        getLogger().info("CoreMC v" + getDescription().getVersion() + " enabled.");

        // --- Register PlaceholderAPI integration if available ---
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            PlaceholderHook.register(this);
            debug("PlaceholderAPI hook registered.");
        } catch (ClassNotFoundException e) {
            debug("PlaceholderAPI not found - placeholders disabled.");
        }
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) playerDataManager.shutdown();
        if (staff != null) staff.shutdown();
        if (crates != null) crates = null;
        if (missions != null) missions.shutdown();
        if (companions != null) companions.shutdown();
        if (events != null) events.shutdown();
        if (islandCore != null) islandCore.shutdown();
        if (ban != null) ban.shutdown();
        if (omnitools != null) omnitools.shutdown();
        if (spawners != null) spawners.shutdown();
        if (gens != null) gens.shutdown();
        if (progression != null) progression.shutdown();
        if (islands != null) islands.shutdown();
        instance = null;
        getLogger().info("CoreMC disabled.");
    }

    public static CoreMC getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CoreMC is not enabled.");
        }
        return instance;
    }

    // --- Foundation ---

    public CoreFoundation foundation() { return foundation; }
    public Messages messages() { return messages; }
    public PlayerDataManager playerData() { return playerDataManager; }
    public PlayerDataManager playerDataManager() { return playerDataManager; }

    // --- Gameplay subsystems ---

    public IslandModule islands() { return islands; }
    public ProgressionModule progression() { return progression; }
    public RoleSetManager roleSets() { return roleSets; }
    public GensModule gens() { return gens; }
    public SpawnersModule spawners() { return spawners; }
    public OmnitoolsModule omnitools() { return omnitools; }
    public BanModule ban() { return ban; }
    public StaffModule staff() { return staff; }
    public CrateModule crates() { return crates; }
    public MissionsModule missions() { return missions; }
    public CompanionModule companions() { return companions; }
    public EventManager events() { return events; }
    public IslandCoreModule islandCore() { return islandCore; }

    /** Called by PlayerDataModule once its manager is constructed. */
    public void setPlayerDataManager(final PlayerDataManager mgr) {
        this.playerDataManager = mgr;
    }

    /**
     * Convenience: expand a CoreMC placeholder in a MiniMessage string.
     * Works with or without PlaceholderAPI present.
     * Safely returns input unchanged if no placeholders match.
     */
    public static String expandPlaceholder(String input, OfflinePlayer player) {
        if (input == null) {
            return input;
        }
        if (PlaceholderHook.getRegistry() == null || PlaceholderHook.getRegistry().isEmpty()) {
            // No PAPI registered — return input unchanged (graceful degradation)
            return input;
        }
        for (var entry : PlaceholderHook.getRegistry().entrySet()) {
            final String token = "%coremc_" + entry.getKey() + "%";
            if (input.contains(token)) {
                final String value = entry.getValue().apply(player);
                input = input.replace(token, value == null ? "" : value);
            }
        }
        return input;
    }

    /**
     * Send a bold-formatted message with the CoreMC prefix from messages.yml.
     * Uses the central Message system so all messages are consistent.
     */
    public void sendMessage(CommandSender sender, String key, Object... vars) {
        if (vars.length > 0) {
            // Build var map from varargs
            final java.util.Map<String, String> varMap = new java.util.HashMap<>();
            for (int i = 0; i < vars.length; i += 2) {
                if (i + 1 < vars.length) {
                    varMap.put(String.valueOf(vars[i]), String.valueOf(vars[i + 1]));
                }
            }
            messages().send(sender, key, varMap);
        } else {
            messages().send(sender, key);
        }
    }

    /**
     * Send a broadcast message with the CoreMC prefix.
     */
    public void broadcastMessage(Plugin source, String key, Object... vars) {
        if (vars.length > 0) {
            final java.util.Map<String, String> varMap = new java.util.HashMap<>();
            for (int i = 0; i < vars.length; i += 2) {
                if (i + 1 < vars.length) {
                    varMap.put(String.valueOf(vars[i]), String.valueOf(vars[i + 1]));
                }
            }
            final String raw = messages().getRaw(key);
            if (raw != null) {
                String resolved = raw;
                for (final var entry : varMap.entrySet()) {
                    resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
                }
                source.getServer().broadcast(net.coremc.foundation.util.ColorUtil.parse(resolved, messages().getPrefix()));
            }
        } else {
            messages().broadcast(source, key);
        }
    }

    /** Debug log — only printed when debug mode is enabled. */
    public void debug(String message) {
        if (foundation != null && foundation.isDebug()) {
            getLogger().info("[DEBUG] " + message);
        }
    }
}