package net.coremc.skyblock.companion;

import net.coremc.coremc.CoreMC;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Awards companion XP from role activities to the player's EQUIPPED companion.
 *
 * <p>Only the equipped companion earns XP (so a player invests in the one they use, and
 * owning many does not equal free multi-progress). XP is granted per valid activity event and
 * is rate-limited per (player, activity) to stop macro spam / trivial actions from maxing
 * companions. Universal companions gain XP from every island activity.</p>
 */
public final class CompanionListener implements Listener {

    private final JavaPlugin plugin;
    private final CompanionManager manager;

    private static final Set<Material> LOGS = Set.of(
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.CRIMSON_STEM, Material.WARPED_STEM);
    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.SUGAR_CANE, Material.CACTUS);
    private static final Set<Material> ORES = Set.of(
            Material.STONE, Material.COBBLESTONE, Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE,
            Material.DIAMOND_ORE, Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS, Material.DEEPSLATE_COAL_ORE,
            Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE);

    // Rate-limit: last counted timestamp per (uuid|activity).
    private final Map<String, Long> lastAction = new ConcurrentHashMap<>();
    private long cooldownMs() {
        return Math.max(0, plugin.getConfig().getLong("companions.action-cooldown-ms", 350));
    }

    public CompanionListener(final JavaPlugin plugin, final CompanionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private boolean throttled(final UUID uuid, final String activity) {
        final long now = System.currentTimeMillis();
        final String k = uuid.toString() + "|" + activity;
        final Long prev = lastAction.get(k);
        if (prev != null && now - prev < cooldownMs()) return true;
        lastAction.put(k, now);
        return false;
    }

    private int xpPer(final String node) {
        return plugin.getConfig().getInt("companions.xp-per-action." + node, 5);
    }

    /** Award XP to the equipped companion if its role matches the activity. */
    private void award(final Player p, final CompanionManager.Role role) {
        final String eqKey = manager.equipped(p.getUniqueId());
        if (eqKey == null) return;
        final String id = eqKey.substring(0, eqKey.lastIndexOf(':'));
        final CompanionManager.Role eqRole = manager.role(id);
        if (eqRole != role && eqRole != CompanionManager.Role.UNIVERSAL) return;
        final CompanionManager.Rarity r = CompanionManager.Rarity.valueOf(
                eqKey.substring(eqKey.lastIndexOf(':') + 1));
        final int base = xpPer(role == CompanionManager.Role.UNIVERSAL ? "universal" : role.name().toLowerCase(Locale.ROOT));
        manager.addXp(p.getUniqueId(), id, r, base);
    }

    @EventHandler
    public void onBreak(final BlockBreakEvent event) {
        final Player p = event.getPlayer();
        if (p == null || throttled(p.getUniqueId(), "break")) return;
        final Block b = event.getBlock();
        if (ORES.contains(b.getType())) {
            award(p, CompanionManager.Role.MINING);
        } else if (LOGS.contains(b.getType())) {
            award(p, CompanionManager.Role.LOGGING);
        } else if (CROPS.contains(b.getType())) {
            award(p, CompanionManager.Role.FARMING);
        }
    }

    @EventHandler
    public void onFish(final PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        final Player p = event.getPlayer();
        if (p == null || throttled(p.getUniqueId(), "fish")) return;
        award(p, CompanionManager.Role.FISHING);
    }

    @EventHandler
    public void onKill(final EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof final Player p)) return;
        if (throttled(p.getUniqueId(), "kill")) return;
        award(p, CompanionManager.Role.SLAYING);
    }
}
