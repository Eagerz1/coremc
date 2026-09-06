package net.coremc.skyblock.core.storage;

import org.bukkit.Location;

import java.util.List;
import java.util.UUID;

/**
 * Persistent island data model.
 */
public final class Island {

    public enum Biome { PLAINS, DESERT, MUSHROOM }

    /** Map our island biome to a Bukkit world biome (best-effort, falls back to PLAINS). */
    public static org.bukkit.block.Biome BiomeToBiome(final Biome biome) {
        try {
            return switch (biome) {
                case DESERT -> org.bukkit.block.Biome.DESERT;
                case MUSHROOM -> org.bukkit.block.Biome.MUSHROOM_FIELDS;
                default -> org.bukkit.block.Biome.PLAINS;
            };
        } catch (final Throwable e) {
            return org.bukkit.block.Biome.PLAINS;
        }
    }

    private final int id;
    private UUID owner;
    private Biome biome;
    private Location spawn;
    private long level;
    private long xp;
    private long createdAt;
    private final List<UUID> members;

    public Island(final int id, final UUID owner, final Biome biome, final Location spawn,
                  final long level, final long xp, final long createdAt, final List<UUID> members) {
        this.id = id;
        this.owner = owner;
        this.biome = biome;
        this.spawn = spawn;
        this.level = level;
        this.xp = xp;
        this.createdAt = createdAt;
        this.members = members;
    }

    public int getId() { return id; }
    public UUID getOwner() { return owner; }
    public void setOwner(final UUID owner) { this.owner = owner; }
    public Biome getBiome() { return biome; }
    public Location getSpawn() { return spawn; }
    public void setSpawn(final Location spawn) { this.spawn = spawn; }
    public long getLevel() { return level; }
    public void setLevel(final long level) { this.level = level; }
    public long getXp() { return xp; }
    public void setXp(final long xp) { this.xp = xp; }
    public long getCreatedAt() { return createdAt; }
    public List<UUID> getMembers() { return members; }

    /** Total members including the owner. */
    public int getMemberCount() {
        return members.size() + 1;
    }
}
