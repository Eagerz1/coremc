package net.coremc.skyblock.missions;

import net.coremc.coremc.CoreMC;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.storage.Island;
import net.coremc.skyblock.gens.generator.GeneratorPlacedEvent;
import net.coremc.skyblock.progression.ProgressionModule;
import net.coremc.skyblock.spawners.spawner.SpawnerPlacedEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Feeds existing SkyBlock activities into the missions system. Each activity
 * increments the matching category's per-player progress. Island-progression
 * missions track the player's island XP (read live from the progression API).
 */
public final class MissionsListener implements Listener {

    private final MissionManager missions;
    private static final Set<Material> LOGS = Set.of(
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.CRIMSON_STEM, Material.WARPED_STEM);
    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART, Material.SUGAR_CANE, Material.CACTUS);
    private static final Set<Material> ORES = Set.of(
            Material.STONE, Material.COBBLESTONE, Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE,
            Material.DIAMOND_ORE, Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS, Material.DEEPSLATE_COAL_ORE,
            Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_DIAMOND_ORE);

    public MissionsListener(final MissionManager missions) {
        this.missions = missions;
    }

    @EventHandler
    public void onBreak(final BlockBreakEvent event) {
        final Player p = event.getPlayer();
        final Material m = event.getBlock().getType();
        if (ORES.contains(m)) {
            bump(p.getUniqueId(), Mission.Category.MINING);
        } else if (LOGS.contains(m)) {
            bump(p.getUniqueId(), Mission.Category.LOGGING);
        } else if (CROPS.contains(m)) {
            bump(p.getUniqueId(), Mission.Category.FARMING);
        }
    }

    @EventHandler
    public void onFish(final PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        bump(event.getPlayer().getUniqueId(), Mission.Category.FISHING);
    }

    @EventHandler
    public void onKill(final EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof final Player p)) return;
        bump(p.getUniqueId(), Mission.Category.SLAYING);
    }

    @EventHandler
    public void onGeneratorPlaced(final GeneratorPlacedEvent event) {
        final IslandApi api = CoreMC.getInstance().islands().api();
        final Island island = api.getIsland(event.islandId());
        if (island != null) {
            bump(island.getOwner(), Mission.Category.GENERATORS);
        }
    }

    @EventHandler
    public void onSpawnerPlaced(final SpawnerPlacedEvent event) {
        final IslandApi api = CoreMC.getInstance().islands().api();
        final Island island = api.getIsland(event.islandId());
        if (island != null) {
            bump(island.getOwner(), Mission.Category.SPAWNERS);
        }
    }

    /** Increment progress for every mission in a category for the player, and refresh island progress. */
    private void bump(final UUID uuid, final Mission.Category category) {
        for (final Mission m : missions.all()) {
            if (m.category() == category) {
                missions.addProgress(uuid, m.id(), 1);
            }
        }
        // Completing objectives also grows the Role-bound Set (config-driven rate; 0 disables).
        final Player p = CoreMC.getInstance().getServer().getPlayer(uuid);
        if (p != null) {
            CoreMC.getInstance().roleSets().awardActivityXp(p, "objective");
        }
        // Keep island-progression missions in sync with live island XP.
        refreshIslandProgress(uuid);
    }

    private void refreshIslandProgress(final UUID uuid) {
        final ProgressionModule prog = CoreMC.getInstance().progression();
        final IslandApi api = CoreMC.getInstance().islands().api();
        final Island island = api.getIsland(uuid);
        if (prog == null || island == null) return;
        final long xp = prog.api().islandXp(island.getId());
        for (final Mission m : missions.all()) {
            if (m.category() == Mission.Category.ISLAND) {
                // Island progress is the island's shared XP (clamped to requirement for display).
                missions.setProgress(uuid, m.id(), xp);
            }
        }
    }
}
