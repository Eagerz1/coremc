package net.coremc.corestaff;
import net.coremc.corestaff.listener.FreezeListener;
import net.coremc.corestaff.listener.StaffListener;
import net.coremc.corestaff.command.*;

import net.coremc.foundation.CoreFoundation;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** Lightweight staff tools module. */
public final class StaffModule {

    private final JavaPlugin plugin;
    private final Set<UUID> vanished = new HashSet<>();
    private final Set<UUID> spectating = new HashSet<>();
    private final Set<UUID> frozen = new HashSet<>();
    private final Map<UUID, org.bukkit.GameMode> priorGamemode = new ConcurrentHashMap<>();
    private final Map<UUID, org.bukkit.Location> priorLocation = new ConcurrentHashMap<>();
    private final java.util.Map<UUID, Integer> cpsCounts = new ConcurrentHashMap<>();
    private final java.util.Map<UUID, BukkitTask> cpsTasks = new ConcurrentHashMap<>();

    public StaffModule(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        CoreFoundation.getInstance().debug("StaffModule enabling");
        plugin.getCommand("invsee").setExecutor(new InvseeCommand(plugin));
        plugin.getCommand("rotate").setExecutor(new RotateCommand(plugin));
        plugin.getCommand("cps").setExecutor(new CpsCommand(plugin, cpsCounts, cpsTasks));
        plugin.getCommand("vanish").setExecutor(new VanishCommand(plugin, vanished));
        plugin.getCommand("spectate").setExecutor(new SpectateCommand(plugin, vanished, spectating, priorGamemode, priorLocation));
        plugin.getCommand("freeze").setExecutor(new FreezeCommand(plugin, frozen));
        plugin.getCommand("captcha").setExecutor(new CaptchaCommand(plugin));
        plugin.getServer().getPluginManager().registerEvents(new FreezeListener(plugin, frozen), plugin);
        plugin.getServer().getPluginManager().registerEvents(new StaffListener(plugin, vanished, spectating), plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> cpsCounts.replaceAll((k, v) -> 0), 20L, 20L);
        plugin.getLogger().info("StaffModule enabled.");
    }

    public void shutdown() {
    }

    public void log(final String message) {
        plugin.getLogger().info("[STAFF] " + message);
    }

    public Set<UUID> vanished() {
        return vanished;
    }

    public Set<UUID> spectating() {
        return spectating;
    }

    public Set<UUID> frozen() {
        return frozen;
    }

    public java.util.Map<UUID, Integer> cpsCounts() {
        return cpsCounts;
    }
}