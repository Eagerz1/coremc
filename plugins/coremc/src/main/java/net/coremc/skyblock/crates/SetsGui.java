package net.coremc.skyblock.crates;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.skyblock.progression.role.RoleManager;
import net.coremc.skyblock.progression.role.RoleSetManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /sets — Role-bound Set progression GUI.
 *
 * <p>Shows the player's Role Set: name, current level, XP, XP required for the
 * next level, progress bar, current set bonuses and the armour-upgrade
 * foundation (per-piece upgrade levels, with requirement scaffolding for future
 * boss-kill / quest / special-material gating).</p>
 *
 * <p>The Set is earned from the chosen Role (not from lootboxes), and grows via
 * Set XP from normal gameplay. All data comes from {@link RoleSetManager} which
 * persists into the permanent player data store.</p>
 */
public final class SetsGui {

    private final JavaPlugin plugin;

    public SetsGui(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(final Player p) {
        final RoleSetManager rsm = CoreMC.getInstance().roleSets();
        final RoleManager.Role role = CoreMC.getInstance().progression().roles().get(p.getUniqueId());

        if (role == null) {
            p.sendMessage(net.coremc.foundation.CoreFoundation.getInstance().messages().parse(
                    "<prefix> <red>Select a Role first (/roles) to receive your Set.</red>",
                    plugin.getConfig().getString("prefix")));
            return;
        }
        final String setId = rsm.setForRole(role);
        if (setId == null || setId.isEmpty()) {
            p.sendMessage(net.coremc.foundation.CoreFoundation.getInstance().messages().parse(
                    "<prefix> <red>This Role has no Set configured yet.</red>",
                    plugin.getConfig().getString("prefix")));
            return;
        }
        if (!rsm.owns(p.getUniqueId(), setId)) {
            // Defensive: grant it (idempotent) in case they somehow never received it.
            rsm.grantRoleSet(p, role);
        }

        final int rows = 6;
        final net.coremc.foundation.gui.InventoryGui gui = new net.coremc.foundation.gui.InventoryGui(
                plugin, rows, "<bold><gold>CoreMC " + rsm.displayName(setId) + " Set</gold></bold>",
                Material.BLACK_STAINED_GLASS_PANE);

        final UUID uuid = p.getUniqueId();
        final int level = rsm.getLevel(uuid, setId);
        final String colour = rsm.colour(setId);

        // ---- Header: Set identity + level ----
        final List<String> head = new ArrayList<>();
        head.add("<" + colour + ">" + rsm.type(setId) + " Set</" + colour + ">");
        head.add("");
        head.add("<white>Level: <gold>" + level + "</gold></white>");
        head.add("<white>XP: <aqua>" + rsm.formatProgress(uuid, setId) + "</aqua></white>");
        head.add("");
        head.add("<gray>" + rsm.progressBar(uuid, setId) + " <white>" + rsm.progressPercent(uuid, setId) + "%</white>");
        final long toNext = rsm.xpToNext(uuid, setId);
        if (toNext < 0) {
            head.add("<green>MAX LEVEL</green>");
        } else {
            head.add("<gray>Next Level: <white>" + String.format(java.util.Locale.ROOT, "%,d", toNext) + " XP</white></gray>");
        }
        gui.setItem(4, ItemUtil.create(Material.LEATHER_CHESTPLATE,
                "<bold><" + colour + ">" + rsm.displayName(setId) + " Set</" + colour + "></bold>", head), ev -> {});

        // ---- Set bonuses ----
        final List<String> bonusLore = new ArrayList<>();
        bonusLore.add("<gray>Set bonuses (grow with level):</gray>");
        for (final String b : rsm.currentBonuses(uuid, setId)) {
            bonusLore.add("<white>• " + b + "</white>");
        }
        gui.setItem(13, ItemUtil.create(Material.ENCHANTED_BOOK,
                "<bold><gold>Set Bonuses</gold></bold>", bonusLore), ev -> {});

        // ---- Armour-piece upgrade foundation (helmet/chest/legs/boots) ----
        final List<String> pieces = rsm.armourPieces();
        int slot = 19;
        for (final String piece : pieces) {
            final int lvl = rsm.getArmourUpgrade(uuid, setId, piece);
            final List<String> pl = new ArrayList<>();
            pl.add("<gray>Upgrade level: <white>" + lvl + "</white></gray>");
            pl.add("");
            pl.add("<gray>Individual armour-piece upgrades are scaffolded for a");
            pl.add("<gray>future system (boss kills / quests / special materials).");
            pl.add("<gray>Not yet claimable.</gray>");
            gui.setItem(slot, armourItem(piece, lvl), ev -> {});
            slot += 2;
        }

        // ---- How to grow ----
        final List<String> grow = new ArrayList<>();
        grow.add("<gray>Gain Set XP by playing your Role:</gray>");
        grow.add("<white>• Killing mobs</white>");
        grow.add("<white>• Mining blocks</white>");
        grow.add("<white>• Farming crops</white>");
        grow.add("<white>• Fishing</white>");
        grow.add("<white>• Completing objectives</white>");
        gui.setItem(40, ItemUtil.create(Material.EXPERIENCE_BOTTLE,
                "<bold><green>Grow Your Set</green></bold>", grow), ev -> {});

        gui.open(p);
    }

    private ItemStack armourItem(final String piece, final int lvl) {
        final Material m = switch (piece) {
            case "helmet" -> Material.LEATHER_HELMET;
            case "chestplate" -> Material.LEATHER_CHESTPLATE;
            case "leggings" -> Material.LEATHER_LEGGINGS;
            case "boots" -> Material.LEATHER_BOOTS;
            default -> Material.LEATHER_CHESTPLATE;
        };
        final String cap = piece.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + piece.substring(1);
        return ItemUtil.create(m, "<bold><yellow>" + cap + "</yellow></bold>",
                List.of("<gray>Upgrade: <white>" + lvl + "</white></gray>"));
    }
}
