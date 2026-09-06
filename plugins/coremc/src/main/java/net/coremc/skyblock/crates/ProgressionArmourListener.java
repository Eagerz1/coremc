package net.coremc.skyblock.crates;

import net.coremc.coremc.CoreMC;
import net.coremc.skyblock.companion.CompanionManager;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;

/**
 * Applies equipped progression-armour and companion drop bonuses to real item drops.
 *
 * <p>Progression armour: Tidal/Fishing, Wildwood/Logging, Deepcore/Mining, Harvest/Farming
 * and Bloodfang/Slaying grant 1.5x drops for their activity; Fortune adds a further 1.1x
 * universal multiplier. Companions (role companions only): Rare Drop Chance and Bonus Drop
 * Chance scale with the equipped companion's level. Block drops use {@link BlockDropItemEvent}
 * (after vanilla loot generation, so Fortune/Silk Touch are respected); mob drops use
 * {@link EntityDeathEvent}.</p>
 */
public final class ProgressionArmourListener implements Listener {

    private final JavaPlugin plugin;
    private final CrateModule module;

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

    public ProgressionArmourListener(final JavaPlugin plugin, final CrateModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    private ProgressionArmour armour() {
        return module.progressionArmour();
    }

    private CompanionManager companions() {
        return CoreMC.getInstance().companions().manager();
    }

    @EventHandler
    public void onDropItems(final BlockDropItemEvent event) {
        final Player p = event.getPlayer();
        if (p == null) return;
        final Material m = event.getBlock().getType();
        final String type;
        if (ORES.contains(m)) type = "Mining";
        else if (LOGS.contains(m)) type = "Logging";
        else if (CROPS.contains(m)) type = "Farming";
        else return;

        final double mult = combinedDropMultiplier(p, type);
        if (mult != 1.0) multiplyItems(event.getItems(), mult);
        applyCompanionDrops(p, event.getItems());
    }

    @EventHandler
    public void onKill(final EntityDeathEvent event) {
        final Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        // Slaying set: 1.5x mob drops (Bloodfang). Universal Fortune applies too.
        final double mult = combinedDropMultiplier(killer, "Slaying");
        if (mult != 1.0) multiplyDrops(event.getDrops(), mult);
        applyCompanionMobDrops(killer, event.getDrops());
    }

    /** Combined drop multiplier = activity-set bonus (1.5x) * Fortune universal bonus (1.1x). */
    private double combinedDropMultiplier(final Player p, final String setType) {
        double mult = armour().dropMultiplier(p, setType);
        mult *= armour().fortuneDropMultiplier(p);
        return mult;
    }

    /** Apply the equipped companion's Rare/Bonus Drop bonuses to a list of dropped Items. */
    private void applyCompanionDrops(final Player p, final List<Item> items) {
        if (items.isEmpty()) return;
        final CompanionManager cm = companions();
        final double rare = cm.rareDropChance(p);
        final double bonus = cm.bonusDropChance(p);
        if (rare <= 0 && bonus <= 0) return;
        final double roll = ThreadLocalRandom_nextDouble();
        // Rare Drop: a lucky roll multiplies every drop by 1.5 (a "rare" bonus yield).
        if (rare > 0 && roll * 100.0 < rare) {
            multiplyItems(items, 1.5);
        }
        // Bonus Drop: a separate roll adds one extra stack of a random drop.
        if (bonus > 0 && ThreadLocalRandom_nextDouble() * 100.0 < bonus) {
            final Item src = items.get(ThreadLocalRandom_nextInt(items.size()));
            final ItemStack copy = src.getItemStack().clone();
            copy.setAmount(Math.max(1, copy.getAmount()));
            final Item extra = src.getWorld().dropItemNaturally(src.getLocation(), copy);
            extra.setCanMobPickup(false);
        }
    }

    /** Apply the equipped companion's Rare/Bonus Drop bonuses to mob-drop ItemStacks. */
    private void applyCompanionMobDrops(final Player p, final List<ItemStack> drops) {
        if (drops.isEmpty()) return;
        final CompanionManager cm = companions();
        final double rare = cm.rareDropChance(p);
        final double bonus = cm.bonusDropChance(p);
        if (rare <= 0 && bonus <= 0) return;
        if (rare > 0 && ThreadLocalRandom_nextDouble() * 100.0 < rare) {
            multiplyDrops(drops, 1.5);
        }
        if (bonus > 0 && ThreadLocalRandom_nextDouble() * 100.0 < bonus) {
            final ItemStack src = drops.get(ThreadLocalRandom_nextInt(drops.size()));
            if (src != null && src.getType() != Material.AIR) {
                final ItemStack copy = src.clone();
                copy.setAmount(Math.max(1, copy.getAmount()));
                drops.add(copy);
            }
        }
    }

    private double ThreadLocalRandom_nextDouble() {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble();
    }

    private int ThreadLocalRandom_nextInt(final int bound) {
        return java.util.concurrent.ThreadLocalRandom.current().nextInt(bound);
    }

    /** Multiply every dropped Item's stack amount by {@code mult} (rounded up, min 1). */
    private void multiplyItems(final List<Item> items, final double mult) {
        for (final Item item : items) {
            final ItemStack it = item.getItemStack();
            if (it == null || it.getType() == Material.AIR) continue;
            final int next = (int) Math.ceil(it.getAmount() * mult);
            it.setAmount(Math.max(1, next));
            item.setItemStack(it);
        }
    }

    /** Multiply every stack's amount by {@code mult} (rounded up, min 1). */
    private void multiplyDrops(final List<ItemStack> drops, final double mult) {
        for (final ItemStack it : drops) {
            if (it == null || it.getType() == Material.AIR) continue;
            final int next = (int) Math.ceil(it.getAmount() * mult);
            it.setAmount(Math.max(1, next));
        }
    }
}
