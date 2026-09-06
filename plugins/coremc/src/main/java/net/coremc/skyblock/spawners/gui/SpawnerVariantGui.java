package net.coremc.skyblock.spawners.gui;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.Economy;
import net.coremc.foundation.util.FormatUtil;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.skyblock.spawners.spawner.PlacedSpawner;
import net.coremc.skyblock.spawners.spawner.SpawnerDef;
import net.coremc.skyblock.spawners.spawner.SpawnerManager;
import net.coremc.skyblock.spawners.spawner.SpawnerVariant;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * The dedicated spawner sub-GUI opened by right-clicking a placed spawner.
 *
 * <p>Displays the four progression/value variants for that mob. Players may
 * switch the placed spawner to any variant they have unlocked. A variant unlocks
 * once the island's progression on the mob reaches the variant's {@code requirement}
 * — higher variants act as accelerators and do NOT require owning every lower
 * variant. If a variant carries a {@code cost} it is charged the first time the
 * player activates it (buying the unlock); switching between already-owned
 * variants is free.</p>
 */
public final class SpawnerVariantGui {

    private final JavaPlugin plugin;
    private final SpawnerManager spawners;
    private final PlacedSpawner placed;

    public SpawnerVariantGui(final JavaPlugin plugin, final SpawnerManager spawners, final PlacedSpawner placed) {
        this.plugin = plugin;
        this.spawners = spawners;
        this.placed = placed;
    }

    public void open(final Player player) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final SpawnerDef def = spawners.get(placed.spawnerId());
        if (def == null) {
            return;
        }
        final int islandId = placed.islandId();
        final InventoryGui gui = new InventoryGui(plugin, 3,
                "<white>" + def.name() + " Variants", Material.BLACK_STAINED_GLASS_PANE);

        final List<SpawnerVariant> variants = def.variantsInOrder();
        int slot = 1;
        for (final SpawnerVariant v : variants) {
            final boolean unlocked = spawners.isVariantUnlocked(islandId, def, v);
            final boolean active = v.id().equals(placed.variant());
            final long prog = spawners.variantProgress(islandId, def);

            final List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("<gray>Mob: <white>" + SpawnerManager.prettyMob(v.effectiveMob(def.mob())));
            lore.add("");
            lore.add("<gray>Core Money / Kill: <yellow>$" + FormatUtil.formatNumber(v.coreMoney()));
            lore.add("<gray>Sky Tokens / Kill: <yellow>" + FormatUtil.formatNumber(v.coreTokens()));
            lore.add("<gray>Progression / Kill: <yellow>x" + v.progressionPerKill());
            lore.add("<gray>Nominal Sell: <yellow>$" + FormatUtil.formatNumber(v.sell()));
            lore.add("");
            lore.add("<gray>Drops:");
            if (v.drops().isEmpty()) {
                lore.add("<red>• <white>none configured");
            } else {
                for (final var d : v.drops()) {
                    lore.add("<red>• <white>" + prettyMat(d.material()) + " x" + d.min()
                            + (d.min() == d.max() ? "" : "-" + d.max()));
                }
            }
            lore.add("");
            if (active) {
                lore.add("<green>ACTIVE VARIANT");
            } else if (unlocked) {
                if (v.cost() > 0) {
                    lore.add("<gray>Unlock Cost: <yellow>$" + FormatUtil.formatNumber((long) v.cost()));
                    lore.add("<gray>• Click to buy & activate");
                } else {
                    lore.add("<gray>• Click to activate");
                }
            } else {
                lore.add("<red>LOCKED");
                lore.add("<gray>Requires: <yellow>" + FormatUtil.formatNumber(v.requirement()) + " progression");
                lore.add("<gray>Have: <white>" + FormatUtil.formatNumber(prog));
            }

            final Material mat = active ? Material.EMERALD_BLOCK
                    : (unlocked ? Material.SPAWNER : Material.BARRIER);
            final ItemStack item = ItemUtil.create(mat, "<bold>" + v.displayName(), lore);
            gui.setItem(slot, item, ev -> {
                if (!unlocked) {
                    cf.messages().sendRaw(player, cf.messages().getPrefix()
                            + " <red>" + v.displayName() + " is locked. Progress to "
                            + FormatUtil.formatNumber(v.requirement()) + " first.");
                    return;
                }
                if (active) {
                    cf.messages().sendRaw(player, cf.messages().getPrefix()
                            + " <gray>" + v.displayName() + " is already active.");
                    return;
                }
                // Charge the unlock cost once (only if it has never been bought for
                // this island — tracked via island stat "variant_bought.<spawner>.<variant>").
                if (v.cost() > 0 && !alreadyBought(islandId, def.id(), v.id())) {
                    final Economy econ = new Economy(plugin);
                    if (!econ.charge(player.getUniqueId(), v.cost(), "spawner-variant:" + def.id() + ":" + v.id())) {
                        cf.messages().sendRaw(player, cf.messages().getPrefix()
                                + " <red>Not enough funds for " + v.displayName() + " ($"
                                + FormatUtil.formatNumber((long) v.cost()) + ").");
                        return;
                    }
                    markBought(islandId, def.id(), v.id());
                }
                spawners.setVariant(locOf(placed), v.id());
                cf.messages().sendRaw(player, cf.messages().getPrefix()
                        + " <green>Activated " + v.displayName() + ".");
                open(player); // refresh
            });
            slot += 3;
        }

        gui.setItem(40, ItemUtil.create(Material.ARROW, "<yellow>Back",
                List.of("<gray>Close this menu.")), ev -> player.closeInventory());

        gui.open(player);
    }

    private boolean alreadyBought(final int islandId, final String spawnerId, final String variantId) {
        return CoreMC.getInstance().islands().api()
                .getStatistic(islandId, "variant_bought." + spawnerId + "." + variantId) >= 1;
    }

    private void markBought(final int islandId, final String spawnerId, final String variantId) {
        CoreMC.getInstance().islands().api()
                .addStatistic(islandId, "variant_bought." + spawnerId + "." + variantId, 1);
    }

    /** Reconstruct the block Location from a PlacedSpawner key ("world:x:y:z"). */
    private org.bukkit.Location locOf(final PlacedSpawner s) {
        final String[] p = s.key().split(":");
        if (p.length < 4) return null;
        final org.bukkit.World w = org.bukkit.Bukkit.getWorld(p[0]);
        if (w == null) return null;
        return new org.bukkit.Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
    }

    private static String prettyMat(final org.bukkit.Material m) {
        final String n = m.name().toLowerCase().replace("_", " ");
        return n.charAt(0) + n.substring(1);
    }
}
