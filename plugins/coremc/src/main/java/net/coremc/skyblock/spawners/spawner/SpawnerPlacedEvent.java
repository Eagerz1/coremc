package net.coremc.skyblock.spawners.spawner;

import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a spawner block is placed on an island (by any method).
 * Used by the missions system to track spawner-placement progress without
 * depending on the right-click placement path.
 */
public final class SpawnerPlacedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Block block;
    private final int islandId;
    private final String spawnerId;

    public SpawnerPlacedEvent(final Block block, final int islandId, final String spawnerId) {
        this.block = block;
        this.islandId = islandId;
        this.spawnerId = spawnerId;
    }

    public Block block() { return block; }
    public int islandId() { return islandId; }
    public String spawnerId() { return spawnerId; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
