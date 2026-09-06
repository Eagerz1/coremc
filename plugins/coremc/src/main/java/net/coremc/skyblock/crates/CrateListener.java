package net.coremc.skyblock.crates;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.skyblock.crates.config.LootboxConfig;
import net.coremc.skyblock.crates.rarity.Rarity;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** Routes physical interactions to the crate system:
 * <ul>
 *   <li>Right-clicking a CoreMC key opens its crate.</li>
 *   <li>Left-clicking a CoreMC key opens the reward-information GUI.</li>
 *   <li>Placing a Lootbox triggers the GUI-based opening animation.</li>
 * </ul>
 */
public final class CrateListener implements Listener {

    private final JavaPlugin plugin;
    private final KeyItem keyItem;
    private final LootboxManager lootbox;
    private final CrateModule crateModule;

    public CrateListener(final JavaPlugin plugin, final KeyItem keyItem, final LootboxManager lootbox,
                         final CrateModule crateModule) {
        this.plugin = plugin;
        this.keyItem = keyItem;
        this.lootbox = lootbox;
        this.crateModule = crateModule;
    }

    @EventHandler
    public void onKeyUse(final PlayerInteractEvent ev) {
        final ItemStack item = ev.getItem();
        if (item == null || !keyItem.isKey(item)) return;

        final KeyId id = keyItem.keyId(item);
        if (id == null) return;

        // LEFT CLICK → Open reward information GUI (do NOT consume key)
        if (ev.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_AIR
                || ev.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            ev.setCancelled(true);
            openRewardGui(ev.getPlayer(), id);
            return;
        }

        // RIGHT CLICK → Open crate (existing behaviour, consumes key)
        if (ev.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                || ev.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            ev.setCancelled(true);
            final Player p = ev.getPlayer();
            final CrateModule module = CoreMC.getInstance().crates();
            if (module.consumeKey(p, id)) {
                module.opener().open(p, id);
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 1f, 1f);
            } else {
                p.sendMessage(CoreFoundation.getInstance().messages().parse(
                        "<prefix> <red>You do not have a " + id.defaultName() + ".</red>",
                        plugin.getConfig().getString("prefix")));
            }
            return;
        }
    }

    @EventHandler
    public void onLootboxPlace(final BlockPlaceEvent ev) {
        final ItemStack item = ev.getItemInHand();
        if (item == null || !lootbox.isLootboxItem(item)) return;
        ev.setCancelled(true);
        final Player p = ev.getPlayer();
        final LootboxConfig cfg = lootbox.get(item);
        if (cfg != null) {
            lootbox.openFromPlace(p, cfg);
        }
    }

    @EventHandler
    public void onLootboxInteract(final PlayerInteractEvent ev) {
        if (ev.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && ev.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final ItemStack item = ev.getItem();
        if (item == null) return;
        // Only handle lootbox items in hand — not keys or other items.
        if (!lootbox.isLootboxItem(item)) return;
        ev.setCancelled(true);
        final Player p = ev.getPlayer();
        final LootboxConfig cfg = lootbox.get(item);
        if (cfg != null) {
            lootbox.openFromPlace(p, cfg);
        }
    }

    /** Open the reward-information GUI for a player with a specific key. */
    private void openRewardGui(final Player p, final KeyId id) {
        // Get the crate config for this key type
        // We need to find the corresponding lootbox config
        // For now, open a generic key reward GUI using the crate module's config
        crateModule.openKeyRewardGui(p, id);
    }
}