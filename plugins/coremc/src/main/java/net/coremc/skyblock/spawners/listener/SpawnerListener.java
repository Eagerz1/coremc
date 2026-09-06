package net.coremc.skyblock.spawners.listener;
import net.coremc.coremc.CoreMC;
import org.bukkit.plugin.java.JavaPlugin;

import net.coremc.foundation.CoreFoundation;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.islandcore.CoreAction;
import net.coremc.skyblock.islandcore.CoreContributionContext;
import net.coremc.skyblock.spawners.spawner.PlacedSpawner;
import net.coremc.skyblock.spawners.spawner.SpawnerManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.UUID;

/**
 * Places spawners on islands (enforcing island protection + spawner cap) and
 * credits progression when spawner-spawned mobs die.
 *
 * <p>Only mobs that originated from a placed spawner advance the kill-based unlock
 * progression. We tag each spawned mob with the spawner's island + spawner id; the
 * death handler reads that tag to attribute the kill.</p>
 */
public final class SpawnerListener implements Listener {

    private final JavaPlugin plugin;
    private final SpawnerManager spawners;
    private static final NamespacedKey SPAWN_TAG = new NamespacedKey("coremc", "cmc_spawn_src");

    public SpawnerListener(final JavaPlugin plugin, final SpawnerManager spawners) {
        this.plugin = plugin;
        this.spawners = spawners;
    }

    /**
     * Right-clicking a placed spawner block (while NOT holding a spawner item)
     * opens that spawner's dedicated variant sub-GUI. Holding a spawner item still
     * places/stacks as before (handled by {@link #onInteract}).
     */
    @EventHandler
    public void onInteractPlaced(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final Player p = event.getPlayer();
        final ItemStack held = p.getInventory().getItemInMainHand();
        // Only open the menu when the player is NOT placing a spawner.
        if (held != null && held.getType() == org.bukkit.Material.SPAWNER) {
            return;
        }
        final Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != org.bukkit.Material.SPAWNER) {
            return;
        }
        final PlacedSpawner at = spawners.getAt(clicked.getLocation());
        if (at == null) {
            return;
        }
        final IslandApi api = CoreMC.getInstance().islands().api();
        final var island = api.getIsland(p.getUniqueId());
        if (island == null || !api.canBuild(p.getUniqueId(), clicked.getLocation())) {
            return;
        }
        event.setCancelled(true);
        new net.coremc.skyblock.spawners.gui.SpawnerVariantGui(plugin, spawners, at).open(p);
    }

    @EventHandler
    public void onInteract(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final Player p = event.getPlayer();
        final ItemStack held = p.getInventory().getItemInMainHand();
        if (held == null || held.getType() != org.bukkit.Material.SPAWNER) {
            return;
        }
        String spawnerId = null;
        String variantId = "normal";
        final var meta = held.getItemMeta();
        if (meta != null) {
            final String tagged = meta.getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "cmc_spawner"), PersistentDataType.STRING);
            if (tagged != null && spawners.get(tagged) != null) {
                spawnerId = tagged;
                final String v = meta.getPersistentDataContainer().get(
                        SpawnerManager.VARIANT_KEY, PersistentDataType.STRING);
                if (v != null) {
                    variantId = v;
                }
            }
        }
        if (spawnerId == null) {
            return;
        }
        final Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        final IslandApi api = CoreMC.getInstance().islands().api();
        final var island = api.islandAt(clicked.getLocation());
        if (island == null || !api.canBuild(p.getUniqueId(), clicked.getLocation())) {
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix() + " <red>You can only place spawners on your own island.");
            return;
        }
        // Shift-click an existing same-type spawner -> stack onto it (in the same block).
        if (p.isSneaking()) {
            final PlacedSpawner existing = spawners.getAt(clicked.getLocation());
            if (existing != null && existing.spawnerId().equals(spawnerId)) {
                final PlacedSpawner added = spawners.addTo(clicked.getLocation(), island.getId(), spawnerId, variantId);
                if (added == null) {
                    CoreFoundation.getInstance().messages().sendRaw(p,
                            CoreFoundation.getInstance().messages().getPrefix()
                                    + " <red>That spawner pile is full (" + spawners.pileMax() + ").");
                    return;
                }
                if (held.getAmount() > 1) {
                    held.setAmount(held.getAmount() - 1);
                } else {
                    p.getInventory().setItemInMainHand(null);
                }
                event.setCancelled(true);
                CoreFoundation.getInstance().messages().sendRaw(p,
                        CoreFoundation.getInstance().messages().getPrefix()
                                + " <green>Stacked (" + added.count() + "/" + spawners.pileMax() + ").");
                return;
            }
            // Sneaking but target is empty/different -> fall through to a normal place on top.
        }
        if (spawners.totalFor(island.getId()) >= spawners.capFor(island.getId())) {
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix()
                            + " <red>Spawner limit reached (" + spawners.capFor(island.getId()) + ").");
            return;
        }
        final Location loc = clicked.getLocation().add(0, 1, 0);
        spawners.place(loc.getBlock(), island.getId(), spawnerId, variantId);
        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
        } else {
            p.getInventory().setItemInMainHand(null);
        }
        event.setCancelled(true);
        CoreFoundation.getInstance().messages().sendRaw(p,
                CoreFoundation.getInstance().messages().getPrefix() + " <green>Placed a spawner.");
    }

    /**
     * Block alternate spawner-placement methods (dispensers, other plugins,
     * creative placement of a spawner block, etc.). Only a spawner block placed
     * inside a valid island region by an owner/member is allowed.
     */
    @EventHandler
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != org.bukkit.Material.SPAWNER) {
            return;
        }
        final Player p = event.getPlayer();
        final IslandApi api = CoreMC.getInstance().islands().api();
        if (api.islandAt(event.getBlockPlaced().getLocation()) == null
                || !api.canBuild(p.getUniqueId(), event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix()
                            + " <red>You can only place spawners on your own island.");
        }
    }

    @EventHandler
    public void onDeath(final EntityDeathEvent event) {
        final Entity e = event.getEntity();
        if (!(e instanceof org.bukkit.entity.LivingEntity)) {
            return;
        }
        final var data = e.getPersistentDataContainer();
        final String src = data.get(SPAWN_TAG, PersistentDataType.STRING);
        if (src == null) {
            return; // wild mob — does not count toward spawner progression
        }
        // A pile may be fed by several spawners: "islandId:spawnerId:variant,...".
        final java.util.List<String[]> contributors = new java.util.ArrayList<>();
        for (final String piece : src.split(",")) {
            final String[] parts = piece.split(":");
            if (parts.length < 3) {
                continue;
            }
            try {
                contributors.add(new String[] { parts[0], parts[1], parts[2] });
            } catch (final Exception ignore) {
                // skip malformed
            }
        }
        if (contributors.isEmpty()) {
            return;
        }
        // Use the first contributor to resolve the mob type / definition / variant.
        final String[] first = contributors.get(0);
        final int islandId = Integer.parseInt(first[0]);
        final String spawnerId = first[1];
        final String variantId = first[2];
        final net.coremc.skyblock.spawners.spawner.SpawnerDef def = spawners.get(spawnerId);
        if (def == null) {
            return;
        }
        final net.coremc.skyblock.spawners.spawner.SpawnerVariant variant = def.variant(variantId);

        // Stack handling: a stacked mob (count > 1) does not fully die — it loses one
        // member and the remainder respawns as a fresh still/no-AI stack entity. Each
        // hit still awards exactly one kill toward progression. All contributing
        // spawners are preserved on the remainder so their progression keeps advancing.
        final int stack = data.getOrDefault(
                net.coremc.skyblock.spawners.spawner.SpawnerManager.STACK_KEY,
                org.bukkit.persistence.PersistentDataType.INTEGER, 0);
        if (stack > 1) {
            final Location spot = e.getLocation();
            final EntityType mobType = variant.effectiveMob(def.mob());
            // Respawn the remainder on the next tick so it doesn't fight the death event.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (spot.getWorld() == null) {
                    return;
                }
                final var mobClass = mobType.getEntityClass();
                if (mobClass == null || !org.bukkit.entity.LivingEntity.class.isAssignableFrom(mobClass)) {
                    return;
                }
                final var remainder = spot.getWorld().spawn(spot,
                        mobClass.asSubclass(org.bukkit.entity.LivingEntity.class));
                if (remainder instanceof org.bukkit.entity.LivingEntity le) {
                    le.getPersistentDataContainer().set(SPAWN_TAG, PersistentDataType.STRING, src);
                    le.getPersistentDataContainer().set(
                            net.coremc.skyblock.spawners.spawner.SpawnerManager.STACK_KEY,
                            org.bukkit.persistence.PersistentDataType.INTEGER, stack - 1);
                    spawners.applyStackAppearance(le, mobType, stack - 1);
                }
            }, 1L);
        }

        // Generate the variant's drops once and hand them to the killer as physical
        // items (they sell them / autoprocess them later). Also sum the drop-derived
        // economy values so they feed the island Core + Island Top.
        final org.bukkit.entity.Entity killer = event.getEntity().getKiller();
        final java.util.Random rnd = new java.util.Random();
        double dropSell = 0, dropCore = 0;
        long dropTokens = 0;
        for (final var drop : variant.drops()) {
            final int amt = drop.rollAmount(rnd);
            if (amt <= 0) continue;
            final ItemStack stack2 = new ItemStack(drop.material(), amt);
            if (killer instanceof final org.bukkit.entity.Player killerP) {
                killerP.getWorld().dropItemNaturally(e.getLocation(), stack2);
            }
            dropSell += drop.moneyValue(amt);
            dropCore += drop.coreValue(amt);
            dropTokens += drop.tokenValue(amt);
        }

        // Advance progression for every contributing spawner (one kill per hit each).
        // The progression credited per kill is scaled by the variant's progression
        // multiplier (e.g. Ancient = 3), but the ACTUAL kill count stays 1.
        final var islandApi = CoreMC.getInstance().islands().api();
        final var core = CoreMC.getInstance().islandCore().service();
        final var prog = net.coremc.coremc.CoreMC.getInstance().progression();
        for (final String[] c : contributors) {
            final int cid = Integer.parseInt(c[0]);
            final net.coremc.skyblock.spawners.spawner.SpawnerDef cdef = spawners.get(c[1]);
            if (cdef == null) {
                continue;
            }
            final net.coremc.skyblock.spawners.spawner.SpawnerVariant cvar = cdef.variant(c[2]);

            // 1) Actual kill statistic (stays 1 regardless of variant) + progression.
            spawners.addKill(cid, cdef.mob(), cvar.progressionPerKill());

            final var isl = islandApi.getIsland(cid);
            if (isl == null) {
                continue;
            }
            islandApi.addStatistic(cid, "mobs-killed", 1); // true kill count
            islandApi.addStatistic(cid, "spawner-mob-activity", 1); // island Top: mob activity
            islandApi.addXp(cid, cvar.xp());

            // 2) Player's own flat reward (pocket money) + Sky Tokens (via Core).
            if (killer instanceof final org.bukkit.entity.Player killerP && cvar.money() > 0) {
                new net.coremc.foundation.util.Economy(plugin).addMoney(killerP.getUniqueId(), cvar.money());
            }
            if (prog != null && cvar.tokens() > 0) {
                prog.api().awardTokens(isl.getOwner(), (long) cvar.tokens());
            }

            // 3) Island Core contribution (belongs to the island, not just the player).
            //    Folds in both the flat variant values AND the drop-derived values.
            if (core != null) {
                final org.bukkit.entity.Player p = (killer instanceof final org.bukkit.entity.Player kp) ? kp : null;
                final UUID pid = (p != null) ? p.getUniqueId()
                        : (isl.getOwner() != null ? isl.getOwner() : null);
                if (pid != null) {
                    core.contribute(CoreContributionContext.builder(cid, pid, CoreAction.MOB_KILL)
                            .source(c[1])
                            .variantId(c[2])
                            .baseMoney(cvar.coreMoney() + dropCore)
                            .baseTokens(cvar.coreTokens() + dropTokens)
                            .kills(cvar.progressionPerKill())
                            .sellValue(cvar.sell() + dropSell)
                            .player(p)
                            .build());
                }
            }
        }
    }

    /** Tag a spawned mob so its death can be attributed to a placed spawner. */
    public static void tagSpawn(final org.bukkit.entity.Entity entity, final int islandId,
                                final String spawnerId, final String variantId) {
        entity.getPersistentDataContainer().set(SPAWN_TAG, PersistentDataType.STRING,
                islandId + ":" + spawnerId + ":" + (variantId == null ? "normal" : variantId));
    }

    public static void tagSpawn(final org.bukkit.entity.Entity entity, final int islandId, final String spawnerId) {
        tagSpawn(entity, islandId, spawnerId, "normal");
    }

    public static NamespacedKey spawnTag() {
        return SPAWN_TAG;
    }
}
