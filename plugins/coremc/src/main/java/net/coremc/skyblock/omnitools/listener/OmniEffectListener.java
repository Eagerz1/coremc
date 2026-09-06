package net.coremc.skyblock.omnitools.listener;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.skyblock.omnitools.ability.AbilityManager;
import net.coremc.skyblock.omnitools.stat.ModifierEngine;
import net.coremc.skyblock.omnitools.stat.Stat;
import net.coremc.skyblock.omnitools.tool.ToolManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Central OmniTool effect listener.
 *
 * <p>All OmniTool stat effects are applied here, sourced from the single
 * {@link ToolManager#buildModifiers} snapshot (no bonus is hardcoded into multiple
 * listeners). It covers:</p>
 * <ul>
 *   <li><b>Haste</b> — sets a haste level on the player scaled by their tool's speed stat
 *       (mining/farming/logging/fishing/universal). Recomputed whenever they hold the tool.</li>
 *   <li><b>Drop / yield bonuses</b> — adds extra drops on break/kill/fish scaled by the
 *       relevant stat multiplier.</li>
 *   <li><b>XP / currency multipliers</b> — multiplies the OmniTool tool-XP and role currency
 *       awarded by {@link ToolListener} (kept here so the modifier is the single source).</li>
 *   <li><b>Active abilities</b> — a Shift+Right-Click WITH an ability available triggers it;
 *       cooldown + feedback handled by {@link AbilityManager}.</li>
 * </ul>
 *
 * <p>Universal is intentionally weaker per-activity: its universal stats are smaller than a
 * specialist's, so it provides a little across the board rather than the full benefit of one
 * role.</p>
 */
public final class OmniEffectListener implements Listener {

    private final ToolManager tools;
    private final AbilityManager abilities;

    public OmniEffectListener(final ToolManager tools, final AbilityManager abilities) {
        this.tools = tools;
        this.abilities = abilities;
    }

    // ---- haste (recomputed on relevant tool interactions) ----

    private void applyHaste(final Player p, final ItemStack held) {
        final String role = tools.roleOf(held);
        if (role == null) return;
        final var snap = tools.buildModifiers(p.getUniqueId(), role,
                abilities.activeModifiers(p.getUniqueId(), role));
        // Choose the speed stat for the held role.
        final Stat speed;
        switch (role) {
            case "MINING" -> speed = Stat.MINING_SPEED;
            case "FARMING" -> speed = Stat.FARMING_SPEED;
            case "LOGGING" -> speed = Stat.LOGGING_SPEED;
            case "FISHING" -> speed = Stat.FISHING_SPEED;
            case "SLAYING" -> speed = Stat.MOB_DAMAGE; // slayer gets damage, not haste
            default -> speed = Stat.UNIVERSAL_SPEED;
        }
        if (speed == Stat.MOB_DAMAGE) {
            p.removePotionEffect(org.bukkit.potion.PotionEffectType.HASTE);
            return;
        }
        final double mult = snap.multiplier(speed);
        if (mult <= 1.0) {
            p.removePotionEffect(org.bukkit.potion.PotionEffectType.HASTE);
            return;
        }
        // +1 haste per ~25% bonus, capped sensibly.
        final int lvl = (int) Math.min(10, Math.max(0, (mult - 1.0) / 0.25));
        if (lvl <= 0) {
            p.removePotionEffect(org.bukkit.potion.PotionEffectType.HASTE);
            return;
        }
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.HASTE, 30 * 20, lvl - 1, true, false));
    }

    @EventHandler
    public void onHoldBreak(final BlockBreakEvent ev) {
        applyHaste(ev.getPlayer(), ev.getPlayer().getInventory().getItemInMainHand());
    }

    // ---- drop / yield bonuses ----

    @EventHandler
    public void onBreakDrops(final BlockBreakEvent ev) {
        final Player p = ev.getPlayer();
        final String role = tools.roleOf(p.getInventory().getItemInMainHand());
        if (role == null) return;
        final Stat st = switch (role) {
            case "MINING" -> Stat.MINING_DROPS;
            case "FARMING" -> Stat.FARMING_YIELD;
            case "LOGGING" -> Stat.LOGGING_YIELD;
            case "UNIVERSAL" -> Stat.UNIVERSAL_YIELD;
            default -> null;
        };
        if (st == null) return;
        final double mult = tools.buildModifiers(p.getUniqueId(), role,
                abilities.activeModifiers(p.getUniqueId(), role)).multiplier(st);
        if (mult > 1.0) {
            final int extra = (int) Math.floor(mult - 1.0 + Math.random() * (mult - 1.0));
            if (extra > 0) {
                for (final var drop : ev.getBlock().getDrops(p.getInventory().getItemInMainHand())) {
                        drop.setAmount(drop.getAmount() * (1 + extra));
                        ev.getBlock().getWorld().dropItemNaturally(ev.getBlock().getLocation(), drop);
                    }
            }
        }
    }

    @EventHandler
    public void onKillDrops(final EntityDeathEvent ev) {
        if (!(ev.getEntity().getKiller() instanceof final Player p)) return;
        final String role = tools.roleOf(p.getInventory().getItemInMainHand());
        if (role == null || (!role.equals("SLAYING") && !role.equals("UNIVERSAL"))) return;
        final Stat st = role.equals("SLAYING") ? Stat.MOB_DROPS : Stat.UNIVERSAL_YIELD;
        final double mult = tools.buildModifiers(p.getUniqueId(), role,
                abilities.activeModifiers(p.getUniqueId(), role)).multiplier(st);
        if (mult > 1.0) {
            final int extra = (int) Math.floor(mult - 1.0 + Math.random() * (mult - 1.0));
            if (extra > 0) {
                ev.getDrops().forEach(d -> d.setAmount(d.getAmount() * (1 + extra)));
            }
        }
    }

    @EventHandler
    public void onFishReward(final PlayerFishEvent ev) {
        if (ev.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        final Player p = ev.getPlayer();
        final String role = tools.roleOf(p.getInventory().getItemInMainHand());
        if (role == null || (!role.equals("FISHING") && !role.equals("UNIVERSAL"))) return;
        final Stat st = role.equals("FISHING") ? Stat.FISHING_REWARDS : Stat.UNIVERSAL_YIELD;
        final double mult = tools.buildModifiers(p.getUniqueId(), role,
                abilities.activeModifiers(p.getUniqueId(), role)).multiplier(st);
        if (mult > 1.0) {
            final int extra = (int) Math.floor(mult - 1.0 + Math.random() * (mult - 1.0));
            if (extra > 0 && ev.getCaught() instanceof final org.bukkit.entity.Item caught) {
                final ItemStack caughtItem = caught.getItemStack();
                caughtItem.setAmount(caughtItem.getAmount() * (1 + extra));
                caught.setItemStack(caughtItem);
            }
        }
    }

    // ---- ability trigger (Shift + Right Click while holding the tool, when an ability is ready) ----

    @EventHandler
    public void onTrigger(final org.bukkit.event.player.PlayerInteractEvent ev) {
        if (ev.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && ev.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final Player p = ev.getPlayer();
        if (!p.isSneaking()) return;
        final String role = tools.roleOf(p.getInventory().getItemInMainHand());
        if (role == null) return;
        // Find the first unlocked, ready ability for this role and trigger it.
        for (final var a : abilities.registry().forRole(role)) {
            if (abilities.isUnlocked(p.getUniqueId(), role, a.id)
                    && abilities.cooldownLeft(p.getUniqueId(), a.id) <= 0) {
                ev.setCancelled(true);
                abilities.trigger(p, role, a.id);
                return;
            }
        }
    }
}
