package net.coremc.skyblock.gens.generator;

import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a generator block is placed on an island (by any method).
 * Used by the missions system to track generator-placement progress without
 * depending on the right-click placement path.
 */
public final class GeneratorPlacedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Block block;
    private final int islandId;
    private final String genId;

    public GeneratorPlacedEvent(final Block block, final int islandId, final String genId) {
        this.block = block;
        this.islandId = islandId;
        this.genId = genId;
    }

    public Block block() { return block; }
    public int islandId() { return islandId; }
    public String genId() { return genId; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
