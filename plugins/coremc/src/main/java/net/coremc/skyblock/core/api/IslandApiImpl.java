package net.coremc.skyblock.core.api;
import net.coremc.coremc.CoreMC;
import org.bukkit.plugin.java.JavaPlugin;


import net.coremc.skyblock.core.storage.Island;
import net.coremc.skyblock.core.storage.IslandStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Default {@link IslandApi} implementation.
 */
public final class IslandApiImpl implements IslandApi {

    private final JavaPlugin plugin;
    private final IslandStorage storage;
    private final net.coremc.skyblock.core.island.IslandManager manager;

    public IslandApiImpl(final JavaPlugin plugin, final IslandStorage storage,
                         final net.coremc.skyblock.core.island.IslandManager manager) {
        this.plugin = plugin;
        this.storage = storage;
        this.manager = manager;
    }

    @Override
    public @Nullable Island getIsland(final @NotNull UUID player) {
        Island is = storage.getIslandByOwner(player);
        if (is == null) {
            is = storage.getIslandByMember(player);
        }
        return is;
    }

    @Override
    public @Nullable Island getIsland(final int id) {
        return storage.getIsland(id);
    }

    @Override
    public void joinIsland(final int islandId, final @NotNull UUID member) {
        storage.addMember(islandId, member);
    }

    @Override
    public @Nullable Island createIsland(final @NotNull UUID owner, final @NotNull Island.Biome biome,
                                         final @NotNull Location spawn) {
        return storage.createIsland(owner, biome, spawn);
    }

    @Override
    public boolean teleportToIsland(final @NotNull Player player) {
        final Island is = getIsland(player.getUniqueId());
        if (is == null || is.getSpawn() == null) {
            return false;
        }
        final Location target = manager.getHome(is, player.getUniqueId());
        player.teleport(target == null ? is.getSpawn() : target);
        return true;
    }

    @Override
    public long addXp(final int islandId, final long amount) {
        storage.addXp(islandId, amount);
        final Island is = storage.getIsland(islandId);
        return is == null ? amount : is.getXp();
    }

    @Override
    public void addStatistic(final int islandId, final @NotNull String key, final long amount) {
        storage.addStatistic(islandId, key, amount);
    }

    @Override
    public long getStatistic(final int islandId, final @NotNull String key) {
        return storage.getStatistic(islandId, key);
    }

    /**
     * Resolve which island region contains the given location.
     * <p>
     * This is purely a spatial lookup: it returns the island whose protected
     * region (centred on its spawn, with its configured radius) contains the
     * location, regardless of who is asking. Returns {@code null} when the
     * location is outside every island — i.e. normal Minecraft world, where
     * island protection must never apply.
     */
    @Override
    public @Nullable Island islandAt(final @NotNull Location location) {
        final org.bukkit.World w = location.getWorld();
        if (w == null) {
            return null;
        }
        for (final Island is : storage.getAllIslands()) {
            final Location spawn = is.getSpawn();
            if (spawn == null || !spawn.getWorld().equals(w)) {
                continue;
            }
            final double radius = manager.getIslandRadius(is);
            final double dx = Math.abs(location.getX() - spawn.getX());
            final double dz = Math.abs(location.getZ() - spawn.getZ());
            if (dx <= radius && dz <= radius) {
                return is;
            }
        }
        return null;
    }

    @Override
    public boolean canBuild(final @NotNull UUID player, final @NotNull Location location) {
        // Outside every island region: normal Minecraft behaviour, never blocked here.
        final Island is = islandAt(location);
        if (is == null) {
            return true;
        }
        // Inside an island: only the owner or a member may build/break.
        return is.getOwner().equals(player) || is.getMembers().contains(player);
    }

    @Override
    public boolean isMember(final @NotNull UUID player, final int islandId) {
        final Island is = storage.getIsland(islandId);
        return is != null && (is.getOwner().equals(player) || is.getMembers().contains(player));
    }

    @Override
    public void addInvite(final @NotNull Island island, final @NotNull UUID target) {
        plugin.getConfig().set("invites." + target.toString(), island.getId());
        plugin.saveConfig();
    }

    @Override
    public int getInvite(final @NotNull UUID target) {
        return plugin.getConfig().getInt("invites." + target.toString(), -1);
    }

    @Override
    public void clearInvite(final @NotNull UUID target) {
        plugin.getConfig().set("invites." + target.toString(), null);
        plugin.saveConfig();
    }

    @Override
    public void setProgressionProvider(final ProgressionProvider provider) {
        CoreMC.getInstance().islands().setProgressionProvider(provider);
    }

    @Override
    public java.util.List<Island> getAllIslands() {
        return storage.getAllIslands();
    }
}
