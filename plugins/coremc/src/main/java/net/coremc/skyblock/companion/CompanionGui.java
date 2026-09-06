package net.coremc.skyblock.companion;

import net.coremc.foundation.util.FormatUtil;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.foundation.gui.InventoryGui;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Clean Companion GUI. Shows all 12 companions in a 6-column grid. Owned companions
 * display name (rarity colour), level/xp, XP-to-next, XP-to-max, current buffs + values,
 * and the XP task; clicking equips (or unequips) them. Locked companions show how to obtain.
 * Rarity colours are used throughout; titles are bold, no italics, normal Minecraft font.
 */
public final class CompanionGui {

    private final JavaPlugin plugin;
    private final CompanionManager manager;

    public CompanionGui(final JavaPlugin plugin, final CompanionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void open(final Player p) {
        final InventoryGui gui = new InventoryGui(plugin, 4,
                "<bold><gold>CoreMC Companions</gold></bold>", Material.BLACK_STAINED_GLASS_PANE);
        final List<String> ids = manager.companionIds();

        for (int i = 0; i < ids.size() && i < 12; i++) {
            final String id = ids.get(i);
            final int col = i % 6;
            final int row = i / 6;
            final int slot = row * 9 + 1 + col;
            gui.setItem(slot, display(p, id), ev -> {
                final Player clicker = (Player) ev.getWhoClicked();
                handleClick(clicker, id);
                gui.open(clicker);
            });
        }
        gui.open(p);
    }

    private ItemStack display(final Player p, final String id) {
        final String colour = manager.rarityColour(highestOwnedRarity(id, p));
        final String name = manager.displayName(id);
        final List<String> lore = new ArrayList<>();
        final CompanionManager.Rarity r = highestOwnedRarity(id, p);

        if (r == null) {
            // Locked.
            lore.add("<gray>Rarity: <white>COMMON</white></gray>");
            lore.add("");
            lore.add("<red>LOCKED</red>");
            lore.add("<gray>Obtain: Companion reward</gray>");
            lore.add("<gray>(crates / events)</gray>");
            return ItemUtil.create(resolveMaterial(id),
                    "<bold><gray>" + name + "</gray></bold>", lore);
        }

        final int lvl = manager.levelOf(p.getUniqueId(), id, r);
        final long cur = manager.xpOf(p.getUniqueId(), id, r);
        final long next = manager.xpForLevel(r, lvl);
        final long toMax = manager.xpToMax(r) - xpInto(r, lvl, cur);
        final boolean eq = isEquipped(p, id, r);

        lore.add("<" + colour + ">Rarity: " + r.name() + "</" + colour + ">");
        lore.add("");
        lore.add("<white>Level: " + lvl + " / " + manager.maxLevel(r) + "</white>");
        lore.add("<white>XP:</white>");
        lore.add("<white>  " + FormatUtil.formatNumber(cur) + " / " + FormatUtil.formatNumber(next) + "</white>");
        lore.add("<white>Next Level: " + FormatUtil.formatNumber(next - cur) + " XP</white>");
        lore.add("<white>To Max: " + FormatUtil.formatNumber(Math.max(0, toMax)) + " XP</white>");
        lore.add("");
        lore.add("<" + colour + ">Buffs:</" + colour + ">");
        for (final String bl : manager.buffLines(id, r, lvl)) lore.add(bl);
        lore.add("");
        lore.add("<white>XP Task: " + manager.xpTask(id) + "</white>");
        lore.add("");
        if (eq) {
            lore.add("<green>EQUIPPED — click to unequip</green>");
        } else {
            lore.add("<gold>Click to equip</gold>");
        }
        return ItemUtil.create(resolveMaterial(id),
                "<bold><" + colour + ">" + name + "</" + colour + "></bold>", lore);
    }

    private void handleClick(final Player p, final String id) {
        final CompanionManager.Rarity r = highestOwnedRarity(id, p);
        if (r == null) {
            p.sendMessage(net.coremc.foundation.CoreFoundation.getInstance().messages().parse(
                    "<prefix> <red>You do not own " + manager.displayName(id) + " yet.</red>",
                    plugin.getConfig().getString("prefix")));
            return;
        }
        if (isEquipped(p, id, r)) {
            manager.unequip(p);
        } else {
            manager.equip(p, id, r);
        }
    }

    /** Highest owned rarity for a companion id (null if unowned). */
    private CompanionManager.Rarity highestOwnedRarity(final String id, final Player p) {
        CompanionManager.Rarity best = null;
        for (final CompanionManager.Rarity r : manager.rarities()) {
            if (manager.ownedCount(p.getUniqueId(), id, r) > 0) best = r;
        }
        return best;
    }

    private boolean isEquipped(final Player p, final String id, final CompanionManager.Rarity r) {
        return (id + ":" + r.name()).equals(manager.equipped(p.getUniqueId()));
    }

    /** Total XP accumulated from level 0 up to current level (for the To-Max display). */
    private long xpInto(final CompanionManager.Rarity r, final int lvl, final long curXp) {
        long total = curXp;
        for (int l = 0; l < lvl; l++) total += manager.xpForLevel(r, l);
        return total;
    }

    private Material resolveMaterial(final String id) {
        try {
            return Material.valueOf(manager.material(id).toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return Material.PAINTING;
        }
    }
}
