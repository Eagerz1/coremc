package net.coremc.skyblock.core.storage;

import org.bukkit.Location;

import java.util.List;
import java.util.UUID;

/**
 * Storage abstraction over islands and per-island statistics.
 */
public interface IslandStorage {

    /** Create a new island and return it (with a generated id). */
    Island createIsland(UUID owner, Island.Biome biome, Location spawn);

    Island getIsland(int id);

    Island getIslandByOwner(UUID owner);

    Island getIslandByMember(UUID member);

    void saveIsland(Island island);

    void deleteIsland(int id);

    void addMember(int islandId, UUID member);

    void removeMember(int islandId, UUID member);

    /** Leaderboard by XP. category: 1=solo,2=duo,3=team. Limit rows. */
    List<Island> getTopIslands(int categoryMinMembers, int limit);

    /** Player rank (1-based) within their category by XP. */
    int getRank(Island island);

    /** Get all loaded islands. */
    List<Island> getAllIslands();

    // ---- statistics ----

    void addStatistic(int islandId, String key, long amount);

    long getStatistic(int islandId, String key);

    // ---- xp ----

    void addXp(int islandId, long amount);

    /** Create tables if they do not exist. */
    void init();

    void close();
}
