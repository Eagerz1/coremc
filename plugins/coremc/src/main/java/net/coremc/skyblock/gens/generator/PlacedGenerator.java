package net.coremc.skyblock.gens.generator;

/**
 * A placed generator instance.
 *
 * <p>{@code count} is how many generators are stacked into this single block. Players
 * shift-click a generator item onto an existing same-type generator to add to the pile
 * (up to {@link GeneratorManager#stackMax()}); shift-left-click picks the whole pile
 * back up as items. The physical block is one GENERATOR block regardless of count, so a
 * pile still counts as a single entry toward the island's generator block cap.</p>
 */
public record PlacedGenerator(String key, int islandId, String genId, int count) {

    public PlacedGenerator withCount(final int newCount) {
        return new PlacedGenerator(key, islandId, genId, newCount);
    }
}
