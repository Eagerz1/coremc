package net.coremc.corelobby;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

/**
 * Generates an empty void world (air everywhere) so the lobby is a true void.
 * Players stand on a small invisible barrier platform placed by CoreLobby at
 * spawn; jumping off drops them into the void, which the plugin catches and
 * teleports them back to the lobby spawn.
 */
public final class VoidGenerator extends ChunkGenerator {

    @Override
    public ChunkData generateChunkData(final World world, final Random random,
                                       final int x, final int z, final BiomeGrid biome) {
        return createChunkData(world); // all-air chunk
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }
}
