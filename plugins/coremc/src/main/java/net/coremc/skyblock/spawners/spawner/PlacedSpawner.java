package net.coremc.skyblock.spawners.spawner;

/**
 * A placed spawner instance.
 *
 * <p>{@code count} is how many spawners are stacked into this single block. Players
 * shift-click a spawner item onto an existing same-type spawner to add to the pile
 * (up to {@link SpawnerManager#pileMax()}); shift-left-click picks the whole pile
 * back up as items. The physical block is one SPAWNER block regardless of count, so a
 * pile still counts as a single entry toward the island's spawner block cap.</p>
 *
 * <p>{@code variant} is the progression/value tier this spawner produces
 * (normal / corrupted / ancient / fourth — see {@link SpawnerVariant}). The variant is
 * stored on the placed record, the item's persistent data, and every spawned mob so
 * kills credit the correct drops, Core money and progression multiplier.</p>
 */
public record PlacedSpawner(String key, int islandId, String spawnerId, int count, String variant) {

    public PlacedSpawner {
        if (variant == null) variant = "normal";
    }

    public PlacedSpawner(final String key, final int islandId, final String spawnerId, final int count) {
        this(key, islandId, spawnerId, count, "normal");
    }

    public PlacedSpawner withCount(final int newCount) {
        return new PlacedSpawner(key, islandId, spawnerId, newCount, variant);
    }

    public PlacedSpawner withVariant(final String newVariant) {
        return new PlacedSpawner(key, islandId, spawnerId, count, newVariant);
    }
}
