package net.coremc.skyblock.progression.listener;

import net.coremc.coremc.CoreMC;
import net.coremc.skyblock.progression.ProgressionModule;
import net.coremc.skyblock.progression.role.RoleManager;
import net.coremc.skyblock.progression.role.RoleProgression;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Awards role XP, Sky Tokens, Core contribution and per-role statistics for role-relevant
 * activities. All rates are configurable in config; the actual award (multipliers, level-ups,
 * milestones, Core routing) is handled centrally by {@link RoleProgression}.
 *
 * <p>A player only earns XP for activities that match their selected role. Universal earns a
 * smaller share from EVERY supported activity (balanced, not the full benefit of any one
 * role). Irrelevant actions are never awarded.</p>
 */
public final class ActivityListener implements Listener {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final ProgressionModule prog;

    private static final Set<Material> LOGS = EnumSet.of(
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.CRIMSON_STEM, Material.WARPED_STEM);
    private static final Set<Material> CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART,
            Material.SUGAR_CANE, Material.CACTUS);
    private static final Set<Material> ORES = EnumSet.of(
            Material.STONE, Material.COBBLESTONE, Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE,
            Material.DIAMOND_ORE, Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS, Material.DEEPSLATE_COAL_ORE,
            Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE);

    // Activities that count as "relevant" perks/mobs for the Slayer role.
    private static final Set<EntityType> HOSTILE = EnumSet.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.ENDERMAN,
            EntityType.WITCH, EntityType.PIGLIN, EntityType.HOGLIN, EntityType.ZOMBIFIED_PIGLIN, EntityType.BLAZE,
            EntityType.GHAST, EntityType.SLIME, EntityType.PHANTOM, EntityType.DROWNED, EntityType.HUSK,
            EntityType.STRAY, EntityType.WITHER_SKELETON, EntityType.CAVE_SPIDER, EntityType.SILVERFISH,
            EntityType.SHULKER, EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN, EntityType.VINDICATOR,
            EntityType.EVOKER, EntityType.PILLAGER, EntityType.RAVAGER, EntityType.WARDEN, EntityType.BOGGED,
            EntityType.BREEZE, EntityType.CREAKING);

    public ActivityListener(final org.bukkit.plugin.java.JavaPlugin plugin, final ProgressionModule prog) {
        this.plugin = plugin;
        this.prog = prog;
    }

    private RoleProgression rp() { return prog.roleProgression(); }

    @EventHandler
    public void onBreak(final BlockBreakEvent event) {
        final Player p = event.getPlayer();
        final RoleManager.Role role = prog.roles().get(p.getUniqueId());
        if (role == null) return;
        final Block b = event.getBlock();
        final Material m = b.getType();

        if (role == RoleManager.Role.MINING && ORES.contains(m)) {
            rp().awardActivity(p, role, "mining", xp("mining"), tokens("mining"), "blocks", 1);
        } else if (role == RoleManager.Role.LOGGING && LOGS.contains(m)) {
            rp().awardActivity(p, role, "logging", xp("logging"), tokens("logging"), "logs", 1);
        } else if (role == RoleManager.Role.FARMING && CROPS.contains(m)) {
            rp().awardActivity(p, role, "farming", xp("farming"), tokens("farming"), "crops", 1);
        } else if (role == RoleManager.Role.UNIVERSAL) {
            // Universal: a smaller share from any supported activity.
            if (ORES.contains(m)) rp().awardActivity(p, role, "mining", uniXp("mining"), uniTokens("mining"), "blocks", 1);
            else if (LOGS.contains(m)) rp().awardActivity(p, role, "logging", uniXp("logging"), uniTokens("logging"), "logs", 1);
            else if (CROPS.contains(m)) rp().awardActivity(p, role, "farming", uniXp("farming"), uniTokens("farming"), "crops", 1);
        }
    }

    @EventHandler
    public void onFish(final PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        final Player p = event.getPlayer();
        final RoleManager.Role role = prog.roles().get(p.getUniqueId());
        if (role == null) return;
        if (role == RoleManager.Role.FISHING) {
            rp().awardActivity(p, role, "fishing", xp("fishing"), tokens("fishing"), "fish", 1);
        } else if (role == RoleManager.Role.UNIVERSAL) {
            rp().awardActivity(p, role, "fishing", uniXp("fishing"), uniTokens("fishing"), "fish", 1);
        }
    }

    @EventHandler
    public void onKill(final EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof final Player p)) return;
        if (!HOSTILE.contains(event.getEntityType())) return; // only relevant mobs count
        final RoleManager.Role role = prog.roles().get(p.getUniqueId());
        if (role == null) return;
        if (role == RoleManager.Role.SLAYING) {
            rp().awardActivity(p, role, "slaying", xp("slaying"), tokens("slaying"), "mobs", 1);
        } else if (role == RoleManager.Role.UNIVERSAL) {
            rp().awardActivity(p, role, "slaying", uniXp("slaying"), uniTokens("slaying"), "mobs", 1);
        }
    }

    // ---- config rate accessors ----

    private long xp(final String activity) {
        // Base role XP per action, before role-perk/event multipliers (those are applied centrally).
        return Math.max(1, (long) plugin.getConfig().getDouble("xp-rates." + activity, 4));
    }

    private long tokens(final String activity) {
        return Math.max(1, (long) plugin.getConfig().getDouble("token-rates." + activity, 3));
    }

    /** Universal earns a fraction of the specialist rate (balanced: small bonus across activities). */
    private long uniXp(final String activity) {
        final double share = plugin.getConfig().getDouble("universal.share.xp", 0.4);
        return Math.max(1, (long) (xp(activity) * share));
    }

    private long uniTokens(final String activity) {
        final double share = plugin.getConfig().getDouble("universal.share.tokens", 0.4);
        return Math.max(1, (long) (tokens(activity) * share));
    }
}
