package net.coremc.skyblock.core.api;
import net.coremc.coremc.CoreMC;

import net.coremc.skyblock.core.storage.Island;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Public API exposed to other CoreMC plugins.
 *
 * <p>Access via {@code CoreMC.getInstance().islands().api()}.</p>
 */
public interface IslandApi {

    /** Get the island a player owns or belongs to, or null. */
    @Nullable Island getIsland(@NotNull UUID player);

    /** Get an island by its numeric id. */
    @Nullable Island getIsland(int id);

    /** Add a player as a member of an existing island. */
    void joinIsland(int islandId, @NotNull UUID member);

    /** Create an island for a player with the given biome. Returns the new island. */
    @Nullable Island createIsland(@NotNull UUID owner, @NotNull Island.Biome biome, @NotNull Location spawn);

    /** Teleport a player to their island spawn (or island home if set). */
    boolean teleportToIsland(@NotNull Player player);

    /** Add XP to an island and persist it. Returns the new total. */
    long addXp(int islandId, long amount);

    /** Add to a named island statistic. */
    void addStatistic(int islandId, @NotNull String key, long amount);

    /** Read a named island statistic. */
    long getStatistic(int islandId, @NotNull String key);

    /** True if a player can build at a location (island member or owner). */
    boolean canBuild(@NotNull UUID player, @NotNull Location location);

    /**
     * Resolve which island region (if any) contains the given location.
     * Returns null when the location is outside every island — i.e. normal
     * Minecraft world, where island protection must NOT apply.
     */
    @Nullable Island islandAt(@NotNull Location location);

    /** True if a player is an island member/owner. */
    boolean isMember(@NotNull UUID player, int islandId);

    /** Record a pending invite from an island to a player. */
    void addInvite(@NotNull Island island, @NotNull UUID target);

    /** Get the island id a player was invited to, or -1. */
    int getInvite(@NotNull UUID target);

    /** Clear a pending invite. */
    void clearInvite(@NotNull UUID target);

    /** Get all loaded islands. */
    java.util.List<Island> getAllIslands();

    /** Register the progression provider (called by skyblock-progression). */
    void setProgressionProvider(ProgressionProvider provider);
}
