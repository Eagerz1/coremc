package net.coremc.skyblock.core.island;
import net.coremc.coremc.CoreMC;
import org.bukkit.plugin.java.JavaPlugin;

import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.api.ProgressionProvider;
import net.coremc.skyblock.core.schematic.SchematicLoader;
import net.coremc.skyblock.core.storage.Island;
import net.coremc.skyblock.core.storage.IslandStorage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.io.InputStream;

/**
 * Island gameplay logic: creation, radius, homes, protection.
 */
public final class IslandManager {

    private final JavaPlugin plugin;
    private final IslandStorage storage;

    // Spacing between island grid slots (generous so upgraded islands never overlap).
    private static final double ISLAND_SPACING = 500.0;

    public IslandManager(final JavaPlugin plugin, final IslandStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public IslandApi getApi() {
        return CoreMC.getInstance().islands().api();
    }

    /** Compute the grid spawn for the next free island slot, in the void world.
     *  Uses a MONOTONIC slot counter so a deleted island's grid position is
     *  never re-used for a freshly created island (otherwise the new island
     *  would paste on top of the old one). */
    public Location nextIslandSpawn() {
        final org.bukkit.World w = net.coremc.skyblock.core.world.SkyblockWorld.getOrCreate(plugin);
        if (w == null) {
            return new Location(plugin.getServer().getWorlds().get(0), 0.5, 100, 0.5);
        }
        final int slot = nextSlot();
        final double spacing = plugin.getConfig().getDouble("island.spacing", ISLAND_SPACING);
        final double x = (slot % 100) * spacing;
        final double z = (slot / 100) * spacing;
        return new Location(w, x + 0.5, 100, z + 0.5);
    }

    /** Monotonic, persisted island-grid slot index. Incremented on every create. */
    private int nextSlot() {
        final int slot = plugin.getConfig().getInt("island.next-slot", 0);
        plugin.getConfig().set("island.next-slot", slot + 1);
        plugin.saveConfig();
        return slot;
    }

    /** Island protection radius in blocks (scales with the size upgrade).
     *  Default is 25.0 → a 50x50 block protected region (±25 from centre). */
    public double getIslandRadius(final Island island) {
        final net.coremc.skyblock.progression.ProgressionModule prog =
                net.coremc.coremc.CoreMC.getInstance().progression();
        int tiers = 0;
        if (prog != null) {
            for (int i = 1; i <= 5; i++) {
                tiers += prog.trees().getLevel(String.valueOf(island.getId()), "island", "island-size-" + i);
            }
        }
        if (tiers > 0) {
            return 25.0 + tiers * 10.0;
        }
        return plugin.getConfig().getDouble("island.default-radius", 25.0);
    }

    /** Per-player island home, defaulting to the island spawn. */
    public Location getHome(final Island island, final UUID player) {
        final String key = "home." + player.toString();
        final String raw = getHomeRaw(island, player);
        if (raw == null) {
            return island.getSpawn();
        }
        final String[] parts = raw.split(",");
        if (parts.length < 3) {
            return island.getSpawn();
        }
        final Location s = island.getSpawn();
        return new Location(s.getWorld(),
                Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                parts.length > 3 ? Float.parseFloat(parts[3]) : s.getYaw(),
                parts.length > 4 ? Float.parseFloat(parts[4]) : s.getPitch());
    }

    private String getHomeRaw(final Island island, final UUID player) {
        return plugin.getConfig().getString("island.homes." + island.getId() + "." + player.toString());
    }

    public void setHome(final Island island, final UUID player, final Location loc) {
        plugin.getConfig().set("island.homes." + island.getId() + "." + player.toString(),
                loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch());
        plugin.saveConfig();
    }

    /** Ratio of island members that triggers team vs duo vs solo category. */
    public int category(final Island island) {
        final int count = island.getMemberCount();
        return count <= 1 ? 1 : (count == 2 ? 2 : 3);
    }

    /** Teleport the player to their island and mark them as inside. */
    public void sendToIsland(final Player player, final Island island) {
        final Location target = getHome(island, player.getUniqueId());
        player.teleport(target == null ? island.getSpawn() : target);
    }

    /**
     * Generate a themed SkyBlock island by pasting the selected SuperiorSkyblock
     * schematic into the dedicated void world, CENTRED on the island spawn.
     *
     * <p>Island creation always pastes the single old-fashioned SkyBlock island
     * schematic (grass platform + cobblestone generator + tree + starting chest),
     * configured by {@code island.create-schematic}. If that file is missing we
     * fall back to the biome-specific schematic, and finally to a generated 50x50
     * platform so players never fall into the void.</p>
     */
    public void buildPlatform(final Island island) {
        buildPlatform(island, true);
    }

    /**
     * @param useCreateSchematic when true, prefer the shared old-style island
     *        schematic ({@code island.create-schematic}) instead of the biome one.
     */
    public void buildPlatform(final Island island, final boolean useCreateSchematic) {
        final Location c = island.getSpawn();
        if (c == null || c.getWorld() == null) {
            return;
        }
        final org.bukkit.World w = c.getWorld();
        final int cx = c.getBlockX();
        final int cz = c.getBlockZ();
        final int baseY = c.getBlockY();

        // Resolve which schematic to paste.
        java.io.File schemFile = null;
        if (useCreateSchematic) {
            schemFile = getCreateSchematicFile();
        }
        if (schemFile == null || !schemFile.exists()) {
            schemFile = getSchematicFile(island.getBiome());
        }
        if (schemFile != null && schemFile.exists()) {
            try {
                final SchematicLoader.SchematicDims dims = SchematicLoader.dimensions(plugin, schemFile);
                final int offX = dims == null ? 0 : dims.width() / 2;
                final int offZ = dims == null ? 0 : dims.length() / 2;
                final Location corner = new Location(w, cx - offX, baseY, cz - offZ);
                SchematicLoader.paste(plugin, schemFile, corner);
                return;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to paste schematic " + schemFile.getName() + ": " + e.getMessage());
                // fall through to generated platform
            }
        }

        // Fallback: a full 50x50 flat island (only if the schematic is missing).
        plugin.getLogger().warning("Schematic missing for " + island.getBiome() + " — generating 50x50 fallback platform.");
        generateFallbackPlatform(w, cx, cz, baseY, island.getBiome());
    }

    /** The shared old-fashioned SkyBlock island schematic used on /is create. */
    private java.io.File getCreateSchematicFile() {
        final String folder = plugin.getConfig().getString("island.schematic-folder", "schematics");
        final String name = plugin.getConfig().getString("island.create-schematic", "island.schem");
        final java.io.File dir = new java.io.File(plugin.getDataFolder(), folder);
        if (!dir.exists()) dir.mkdirs();
        java.io.File file = new java.io.File(dir, name);
        if (file.exists()) return file;
        // Server-root / schematic-folder/<name>
        final java.io.File serverFile = new java.io.File(folder + "/" + name);
        if (serverFile.exists()) return serverFile;
        // Bundled resource copied on demand.
        try (InputStream res = plugin.getResource("schematics/" + name)) {
            if (res != null) {
                java.nio.file.Files.copy(res, file.toPath());
                return file;
            }
        } catch (final Exception ignored) {}
        return file;
    }

    private java.io.File getSchematicFile(Island.Biome biome) {
        final String folder = plugin.getConfig().getString("island.schematic-folder", "schematics");
        final String name = biome.name().toLowerCase() + ".schem";
        // Primary: plugin data folder/schematics/<biome>.schem (extracted on enable)
        java.io.File dir = new java.io.File(plugin.getDataFolder(), folder);
        if (!dir.exists()) dir.mkdirs();
        java.io.File file = new java.io.File(dir, name);
        if (file.exists()) return file;
        // Fallback: server root/schematics/<biome>.schem
        java.io.File serverFile = new java.io.File(folder + "/" + name);
        if (serverFile.exists()) return serverFile;
        // Last resort: bundled resource copied to the data folder on demand
        try (InputStream res = plugin.getResource("schematics/" + name)) {
            if (res != null) {
                java.nio.file.Files.copy(res, file.toPath());
                return file;
            }
        } catch (final Exception ignored) {}
        return file; // return plugin path even if missing (for error reporting)
    }

    private void generateFallbackPlatform(World w, int cx, int cz, int baseY, Island.Biome biome) {
        final org.bukkit.Material surface;
        final org.bukkit.Material sub;
        switch (biome) {
            case DESERT -> { surface = org.bukkit.Material.SAND; sub = org.bukkit.Material.SANDSTONE; }
            case MUSHROOM -> { surface = org.bukkit.Material.MYCELIUM; sub = org.bukkit.Material.DIRT; }
            default -> { surface = org.bukkit.Material.GRASS_BLOCK; sub = org.bukkit.Material.DIRT; }
        }
        // Full 50x50 island (radius 24) centred on the spawn — never a tiny pad.
        final int r = 24;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                final int bx = cx + dx;
                final int bz = cz + dz;
                final boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
                w.getBlockAt(bx, baseY - 3, bz).setType(org.bukkit.Material.BEDROCK);
                w.getBlockAt(bx, baseY - 2, bz).setType(sub);
                w.getBlockAt(bx, baseY - 1, bz).setType(sub);
                w.getBlockAt(bx, baseY, bz).setType(edge ? sub : surface);
                w.getBlockAt(bx, baseY + 1, bz).setType(org.bukkit.Material.AIR);
                w.getBlockAt(bx, baseY + 2, bz).setType(org.bukkit.Material.AIR);
            }
        }
        w.getBlockAt(cx, baseY + 1, cz).setType(org.bukkit.Material.OAK_LOG);
        w.getBlockAt(cx, baseY + 2, cz).setType(org.bukkit.Material.OAK_LOG);
        w.getBlockAt(cx, baseY + 3, cz).setType(org.bukkit.Material.OAK_LEAVES);
        final org.bukkit.block.Block chest = w.getBlockAt(cx + 2, baseY + 1, cz);
        chest.setType(org.bukkit.Material.CHEST);
        try {
            final org.bukkit.block.Chest chestState = (org.bukkit.block.Chest) chest.getState();
            chestState.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.OAK_SAPLING, 4));
            chestState.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BREAD, 8));
            chestState.update();
        } catch (Throwable ignored) {}
    }

    /** Flat spawn at island centre (used when no separate home is set). */
    public Location centre(final Location spawn, final double radius) {
        return spawn.clone();
    }
}