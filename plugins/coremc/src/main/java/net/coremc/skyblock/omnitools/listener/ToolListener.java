package net.coremc.skyblock.omnitools.listener;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.skyblock.omnitools.OmniConfig;
import net.coremc.skyblock.omnitools.gui.OmniMenu;
import net.coremc.skyblock.omnitools.tool.ToolManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/** Awards role currency + per-Omnitool tool XP on activity, feeds island progression,
 * and opens the Omnitool menu on Shift + Right Click with an Omnitool in hand.
 *
 * <p>Currency is role-specific and separate from money/credits/tokens. Tool Level is
 * per-Omnitool and separate from the global Role Level. Universal earns tool XP from
 * every supported activity (balanced, not the full benefit of any single role).</p>
 */
public final class ToolListener implements Listener {
    private static final Set<Material> ORES = EnumSet.of(
            Material.STONE, Material.COBBLESTONE, Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE,
            Material.DIAMOND_ORE, Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS, Material.DEEPSLATE_COAL_ORE,
            Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE);
    private static final Set<Material> CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART,
            Material.SUGAR_CANE, Material.CACTUS);
    private static final Set<Material> LOGS = EnumSet.of(
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.CRIMSON_STEM, Material.WARPED_STEM);
    private final ToolManager tools;
    private final OmniMenu menu;
    private final OmniConfig omni;

    public ToolListener(final ToolManager tools, final OmniMenu menu, final OmniConfig omni) {
        this.tools = tools;
        this.menu = menu;
        this.omni = omni;
    }

    @EventHandler
    public void onInteract(final PlayerInteractEvent ev) {
        if (ev.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && ev.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final Player p = ev.getPlayer();
        if (!p.isSneaking()) return; // Shift required
        final String role = tools.roleOf(p.getInventory().getItemInMainHand());
        if (role == null) return;
        ev.setCancelled(true);
        menu.openMain(p, role);
    }

    @EventHandler
    public void onBreak(final BlockBreakEvent event) {
        final Player p = event.getPlayer();
        final String role = tools.roleOf(p.getInventory().getItemInMainHand());
        if (role == null) return;
        final Block b = event.getBlock();
        final Material m = b.getType();
        if (role.equals("MINING") && ORES.contains(m)) {
            award(p, role, "mining");
        } else if (role.equals("LOGGING") && LOGS.contains(m)) {
            award(p, role, "logging");
        } else if (role.equals("FARMING") && CROPS.contains(m)) {
            award(p, role, "farming");
        } else if (role.equals("UNIVERSAL")) {
            if (ORES.contains(m)) award(p, role, "mining");
            else if (LOGS.contains(m)) award(p, role, "logging");
            else if (CROPS.contains(m)) award(p, role, "farming");
        }
    }

    @EventHandler
    public void onKill(final EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof final Player p)) return;
        final String role = tools.roleOf(p.getInventory().getItemInMainHand());
        if (role == null) return;
        if (role.equals("SLAYING")) {
            award(p, role, "slaying");
        } else if (role.equals("UNIVERSAL")) {
            award(p, role, "slaying");
        }
    }

    @EventHandler
    public void onFish(final PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        final Player p = event.getPlayer();
        final String role = tools.roleOf(p.getInventory().getItemInMainHand());
        if (role == null) return;
        if (role.equals("FISHING")) {
            award(p, role, "fishing");
        } else if (role.equals("UNIVERSAL")) {
            award(p, role, "fishing");
        }
    }

    /** Award role currency + tool XP for an activity with the given Omnitool. */
    private void award(final Player p, final String role, final String activity) {
        final long base = Math.max(1, (long) omni.config()
                .getDouble("omnitools.xp-rewards." + (role.equals("UNIVERSAL") ? "universal" : activity), 4));
        final int before = tools.toolLevel(p.getUniqueId(), role);
        final int after = tools.addToolXp(p.getUniqueId(), role, base);
        // Role currency earned by the activity (separate from money/credits/tokens).
        tools.currency().add(p.getUniqueId(), role, base);

        // Feed island progression when on an island (unchanged behaviour).
        final var island = CoreMC.getInstance().islands().api().getIsland(p.getUniqueId());
        if (island != null) {
            CoreMC.getInstance().islands().api().addXp(island.getId(), base / 5);
            CoreMC.getInstance().islands().api().addStatistic(island.getId(), "tool-xp", base);
        }
        if (after > before) {
            CoreFoundation.getInstance().messages().sendRaw(p,
                    CoreFoundation.getInstance().messages().getPrefix()
                            + " <green>" + roleName(role) + " Omnitool reached Tool Level " + after + "!</green>");
        }
    }

    private String roleName(final String role) {
        final String n = omni.config().getString("omnitools.roles." + role + ".name", role);
        return n;
    }

    @EventHandler
    public void onDrop(final PlayerDropItemEvent event) {
        final String role = tools.roleOf(event.getItemDrop().getItemStack());
        if (role != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(final org.bukkit.event.inventory.InventoryClickEvent event) {
        // Prevent clicking OmniTools out of the grid that would cause dropping
        if (event.getSlotType() == InventoryType.SlotType.QUICKBAR || event.getSlotType() == InventoryType.SlotType.CRAFTING) {
            final String role = tools.roleOf(event.getCursor() != null ? event.getCursor() : event.getView().getItem(event.getSlot()));
            if (role != null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryMove(final org.bukkit.event.inventory.InventoryMoveItemEvent event) {
        // Prevent moving OmniTools between slots in ways that could cause dropping
        final String role = tools.roleOf(event.getItem());
        if (role != null) {
            event.setCancelled(true);
        }
    }
}