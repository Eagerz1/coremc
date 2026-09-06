package net.coremc.skyblock.islandcore;

import net.coremc.coremc.CoreMC;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Foundation module for the Island Core system.
 *
 * <p>Owns the {@link CoreService} and wires the default buff/event providers so
 * the Core is immediately compatible with the existing island buff and live
 * event architectures. Other systems (spawners, activity, island-top) reach the
 * service via {@link #service()}.</p>
 *
 * <p>This module intentionally contains NO gameplay logic of its own — it is the
 * shared foundation that mob kills, mining, farming, logging, fishing and future
 * actions all contribute into.</p>
 */
public final class IslandCoreModule {

    private final JavaPlugin plugin;
    private CoreService service;

    public IslandCoreModule(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        this.service = new CoreService(plugin);
        // Bridge to the existing architecture (identity until operators add the
        // corresponding tree nodes / event types).
        service.setBuffApi(new CoreBuffFromProgression());
        service.setEventApi(new CoreEventsFromManager());
        CoreMC.getInstance().getLogger().info("IslandCoreModule enabled.");
    }

    public void shutdown() {
        // Nothing pooled; player/island data is flushed by their own managers.
    }

    public CoreService service() {
        return service;
    }
}
