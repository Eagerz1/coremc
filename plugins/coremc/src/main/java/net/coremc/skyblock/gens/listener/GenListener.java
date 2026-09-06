package net.coremc.skyblock.gens.listener;
import org.bukkit.plugin.java.JavaPlugin;
import net.coremc.coremc.CoreMC;

import net.coremc.foundation.CoreFoundation;
import net.coremc.skyblock.core.api.IslandApi;

import net.coremc.skyblock.gens.generator.GeneratorDef;
import net.coremc.skyblock.gens.generator.GeneratorManager;
import net.coremc.skyblock.gens.generator.PlacedGenerator;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Places a generator when a player right-clicks a block on their island while
 * holding a generator item. Enforces the island generator cap and island
 * protection (cannot place outside the island region).
 */
public final class GenListener implements Listener {

    private final JavaPlugin plugin;
    private final GeneratorManager generators;

    public GenListener(final JavaPlugin plugin, final GeneratorManager generators) {
        this.plugin = plugin;
        this.generators = generators;
    }

    @EventHandler
    public void onInteract(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final Player p = event.getPlayer();
        final ItemStack held = p.getInventory().getItemInMainHand();
        if (held == null || held.getType() == org.bukkit.Material.AIR) {
            return;
        }
        // Identify the generator by its persistent tag (robust to material overlaps).
        String genId = null;
        final var meta = held.getItemMeta();
        if (meta != null) {
            final String tagged = meta.getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "cmc_gen"), PersistentDataType.STRING);
            if (tagged != null && generators.get(tagged) != null) {
                genId = tagged;
            }
        }
        if (genId == null) {
            // Fall back to material match.
            for (final GeneratorDef def : generators.definitions()) {
                if (def.material() == held.getType()) {
                    genId = def.id();
                    break;
                }
            }
        }
        if (genId == null) {
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
                    CoreFoundation.getInstance().messages().getPrefix() + " <red>You can only place generators on your own island.");
            return;
        }
        // Shift-click an existing same-type generator -> stack onto it (in the same block).
        if (p.isSneaking()) {
            final PlacedGenerator existing = generators.getAt(clicked.getLocation());
            if (existing != null && existing.genId().equals(genId)) {
                final PlacedGenerator added = generators.addTo(clicked.getLocation(), island.getId(), genId);
                if (added == null) {
                    CoreFoundation.getInstance().messages().sendRaw(p,
                            CoreFoundation.getInstance().messages().getPrefix()
                                    + " <red>That generator pile is full (" + generators.stackMax() + ").");
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
                                + " <green>Stacked (" + added.count() + "/" + generators.stackMax() + ").");
                return;
            }
            // Sneaking but target is empty/different -> fall through to a normal place on top.
        }
        if (generators.totalFor(island.getId()) >= generators.capFor(island.getId())) {
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix()
                            + " <red>Generator limit reached (" + generators.capFor(island.getId()) + ").");
            return;
        }
        // Place on top of the clicked block.
        final Location loc = clicked.getLocation().add(0, 1, 0);
        generators.place(loc.getBlock(), island.getId(), genId);
        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
        } else {
            p.getInventory().setItemInMainHand(null);
        }
        event.setCancelled(true);
        CoreFoundation.getInstance().messages().sendRaw(p,
                CoreFoundation.getInstance().messages().getPrefix() + " <green>Placed a generator.");
    }

    /**
     * Block alternate generator-placement methods (dispensers, other plugins,
     * creative placement of a generator block, etc.). Only a generator block
     * placed inside a valid island region by an owner/member is allowed.
     */
    @EventHandler
    public void onBlockPlace(final BlockPlaceEvent event) {
        final org.bukkit.block.Block block = event.getBlockPlaced();
        final org.bukkit.Material mat = block.getType();
        boolean isGen = false;
        for (final GeneratorDef def : generators.definitions()) {
            if (def.material() == mat) {
                isGen = true;
                break;
            }
        }
        if (!isGen) {
            return;
        }
        final Player p = event.getPlayer();
        final IslandApi api = CoreMC.getInstance().islands().api();
        if (api.islandAt(block.getLocation()) == null
                || !api.canBuild(p.getUniqueId(), block.getLocation())) {
            event.setCancelled(true);
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix()
                            + " <red>You can only place generators on your own island.");
        }
    }
}
