package net.coremc.skyblock.core.world;

import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Manages the dedicated SkyBlock void world. Islands are generated here, fully
 * separated from the normal overworld. The world is a flat void (air) so only
 * generated island platforms exist.
 */
public final class SkyblockWorld {

    public static final String NAME = "skyblock";

    private SkyblockWorld() {}

    /** Get (creating if necessary) the SkyBlock void world. */
    public static World getOrCreate(final JavaPlugin plugin) {
        World w = plugin.getServer().getWorld(NAME);
        if (w != null) {
            return w;
        }
        final WorldCreator creator = new WorldCreator(NAME)
                .environment(World.Environment.NORMAL)
                .generator(new VoidChunkGenerator());
        w = plugin.getServer().createWorld(creator);
        if (w == null) {
            plugin.getLogger().severe("Failed to create SkyBlock void world '" + NAME + "'");
            return null;
        }
        w.setSpawnLocation(0, 100, 0);
        w.setKeepSpawnInMemory(false);
        return w;
    }
}
