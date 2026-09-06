package net.coremc.skyblock.core.world;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Produces an empty void world (air everywhere). Island platforms are placed
 * explicitly by the island system; nothing is generated automatically.
 */
public final class VoidChunkGenerator extends ChunkGenerator {

    @Override
    public void generateSurface(final WorldInfo worldInfo, final Random random,
                                final int chunkX, final int chunkZ, final ChunkData chunkData) {
        // air only
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

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
