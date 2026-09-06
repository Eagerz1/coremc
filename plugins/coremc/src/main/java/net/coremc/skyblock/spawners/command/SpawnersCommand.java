package net.coremc.skyblock.spawners.command;
import org.bukkit.plugin.java.JavaPlugin;
import net.coremc.coremc.CoreMC;

import net.coremc.foundation.CoreFoundation;
import net.coremc.foundation.gui.InventoryGui;
import net.coremc.foundation.util.Economy;
import net.coremc.foundation.util.FormatUtil;
import net.coremc.foundation.util.ItemUtil;
import net.coremc.skyblock.core.api.IslandApi;
import net.coremc.skyblock.core.storage.Island;

import net.coremc.skyblock.spawners.spawner.SpawnerDef;
import net.coremc.skyblock.spawners.spawner.SpawnerManager;
import net.coremc.skyblock.spawners.spawner.SpawnerVariant;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /spawners - progression shop GUI for the spawner tiers.
 *
 * <p>The main menu lists every tier with LOCKED/UNLOCKED state and kill progress toward
 * the next unlock. Clicking a tier opens its variant sub-GUI, which shows the four
 * variants (Normal, Corrupted, Ancient, Fourth) of that mob with name, status, cost,
 * requirement, progression multiplier and main benefit, then grants the chosen variant
 * spawner item (charging the variant cost on first activation).</p>
 */
public final class SpawnersCommand implements org.bukkit.command.CommandExecutor,
        org.bukkit.command.TabCompleter {

    private final JavaPlugin plugin;
    private final SpawnerManager spawners;
    private final Economy economy;

    public SpawnersCommand(final JavaPlugin plugin, final SpawnerManager spawners) {
        this.plugin = plugin;
        this.spawners = spawners;
        this.economy = new Economy(plugin);
    }

    @Override
    public boolean onCommand(final org.bukkit.command.CommandSender sender,
                             final org.bukkit.command.Command cmd, final String label,
                             final String[] args) {
        if (!(sender instanceof final Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        final CoreFoundation cf = CoreFoundation.getInstance();
        final Island island = CoreMC.getInstance().islands().api().getIsland(player.getUniqueId());
        if (island == null) {
            cf.messages().sendRaw(player, cf.messages().getPrefix() + " <red>You need an island first.");
            return true;
        }
        openTiers(player, island);
        return true;
    }

    // ---- main tier menu ----

    public void openTiers(final Player player, final Island island) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final InventoryGui gui = new InventoryGui(plugin, 6,
                "<white>Spawners", Material.BLACK_STAINED_GLASS_PANE);

        final int cap = spawners.capFor(island.getId());
        final int owned = spawners.totalFor(island.getId());
        final List<String> headLore = new ArrayList<>();
        headLore.add("<gray>Current limit: <yellow>" + cap);
        headLore.add("<gray>Placed: <white>" + owned + " <gray>/ <white>" + cap);
        headLore.add("");
        headLore.add("<gray>Click a mob to view its variants.");
        gui.setItem(4, ItemUtil.create(Material.ENDER_CHEST, "<bold>Spawner Limit", headLore), null);

        int slot = 8;
        for (final SpawnerDef def : spawners.definitions()) {
            if (slot >= 54) {
                break;
            }
            final boolean unlocked = spawners.isUnlocked(island.getId(), def);
            final int ownedType = spawners.countFor(island.getId(), def.id());
            final List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("<gray>Mob:");
            lore.add("<red>• <white>" + SpawnerManager.prettyMob(def.mob()));
            lore.add("");
            lore.add("<gray>Variants: <white>4 <gray>(Normal / Uncommon / Rare / " + def.variant(SpawnerVariant.ANCIENT).displayName() + ")");
            lore.add("");
            if (!unlocked) {
                final SpawnerDef prev = prevDef(def);
                final long have = prev == null ? 0 : spawners.killsFor(island.getId(), prev.mob());
                lore.add("<gray>Progress:");
                lore.add("<aqua>• <white>" + FormatUtil.formatNumber(have) + " <gray>/ <white>"
                        + FormatUtil.formatNumber(def.requirement()));
                lore.add("");
                lore.add("<red>Status: LOCKED");
            } else {
                lore.add("<gray>Owned: <white>" + ownedType);
                lore.add("");

                if (owned >= cap) {
                    lore.add("<red>Status: LIMIT REACHED (" + owned + "/" + cap + ")");
                } else {
                    lore.add("<green>Status: UNLOCKED");
                    lore.add("<gray>• Click to view variants");
                }
            }
            final Material mat = unlocked ? Material.SPAWNER : Material.BARRIER;
            gui.setItem(slot, ItemUtil.create(mat, "<bold>" + def.name(), lore),
                    ev -> {
                        if (!unlocked) {
                            cf.messages().sendRaw(player, cf.messages().getPrefix()
                                    + " <red>" + def.name() + " is locked. Kill more "
                                    + (prevDef(def) == null ? "" : SpawnerManager.prettyMob(prevDef(def).mob())) + " first.");
                            return;
                        }
                        openVariants(player, island, def);
                    });
            slot += 2;
            if (slot == 16 || slot == 25 || slot == 34 || slot == 43) {
                slot += 2;
            }
        }
        gui.open(player);
    }

    // ---- per-mob variant sub-GUI ----

    public void openVariants(final Player player, final Island island, final SpawnerDef def) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final int islandId = island.getId();
        final InventoryGui gui = new InventoryGui(plugin, 6,
                "<white>" + def.name() + " Variants", Material.BLACK_STAINED_GLASS_PANE);

        long reqForPrev = 0;
        final SpawnerDef prev = prevDef(def);
        if (prev != null) {
            reqForPrev = prev.requirement();
        }

        int slot = 8;
        for (final SpawnerVariant v : def.variantsInOrder()) {
            if (slot >= 54) {
                break;
            }
            final boolean unlocked = spawners.isVariantUnlocked(islandId, def, v);
            final boolean bought = alreadyBought(islandId, def.id(), v.id());
            final long prog = spawners.variantProgress(islandId, def);

            final List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("<bold>" + v.displayName().toUpperCase());
            lore.add("");
            lore.add("<gray>Status: " + (unlocked ? "<green>UNLOCKED" : "<red>LOCKED"));
            lore.add("");
            if (!unlocked) {
                lore.add("<gray>Requirement:");
                lore.add("<yellow>• <white>" + FormatUtil.formatNumber(v.requirement()) + " "
                        + SpawnerManager.prettyMob(def.mob()) + " Progression");
                lore.add("<gray>Have: <white>" + FormatUtil.formatNumber(prog) + " <gray>/ <white>"
                        + FormatUtil.formatNumber(v.requirement()));
            } else if (!bought && v.cost() > 0) {
                lore.add("<gray>Cost: <yellow>$" + FormatUtil.formatNumber((long) v.cost()));
            } else {
                lore.add("<gray>Cost: <green>FREE (already owned)");
            }
            lore.add("");
            lore.add("<gray>Progression: <yellow>x" + v.progressionPerKill());
            lore.add("");
            lore.add("<gray>Core Money / Kill: <yellow>$" + FormatUtil.formatNumber((long) v.coreMoney()));
            lore.add("<gray>Sky Tokens / Kill: <yellow>" + FormatUtil.formatNumber(v.coreTokens()));
            lore.add("");
            lore.add("<gray>Benefit: <white>" + (v.benefit().isEmpty()
                    ? "Higher-value drops & faster unlocks" : v.benefit()));
            lore.add("");
            lore.add("<gray>Drops:");
            if (v.drops().isEmpty()) {
                lore.add("<red>• <white>none configured");
            } else {
                for (final var d : v.drops()) {
                    lore.add("<red>• <white>" + prettyMat(d.material()) + " x" + d.min()
                            + (d.min() == d.max() ? "" : "-" + d.max())
                            + (d.chance() < 1.0 ? " <gray>(" + (int) (d.chance() * 100) + "%)" : ""));
                }
            }
            lore.add("");
            lore.add(unlocked ? "<gray>• Click to buy & place" : "<gray>• Unlock it by gaining more progression");

            final Material mat = unlocked ? Material.SPAWNER : Material.BARRIER;
            gui.setItem(slot, ItemUtil.create(mat, "<bold>" + v.displayName(), lore),
                    ev -> {
                        if (!unlocked) {
                            cf.messages().sendRaw(player, cf.messages().getPrefix()
                                    + " <red>" + v.displayName() + " is locked. Reach "
                                    + FormatUtil.formatNumber(v.requirement()) + " progression first.");
                            return;
                        }
                        if (v.cost() > 0 && !alreadyBought(islandId, def.id(), v.id())) {
                            if (!economy.charge(player.getUniqueId(), v.cost(),
                                    "spawner-variant:" + def.id() + ":" + v.id())) {
                                cf.messages().sendRaw(player, cf.messages().getPrefix()
                                        + " <red>Not enough money for " + v.displayName() + " ($"
                                        + FormatUtil.formatNumber((long) v.cost()) + ").");
                                return;
                            }
                            markBought(islandId, def.id(), v.id());
                        }
                        if (spawners.totalFor(islandId) >= spawners.capFor(islandId)) {
                            cf.messages().sendRaw(player, cf.messages().getPrefix()
                                    + " <red>Spawner limit reached (" + spawners.capFor(islandId) + ").");
                            return;
                        }
                        spawners.give(player, def.id(), v.id());
                        cf.messages().sendRaw(player, cf.messages().getPrefix()
                                + " <green>Bought " + def.name() + " - " + v.displayName() + ". Place it!");
                        openVariants(player, CoreMC.getInstance().islands().api().getIsland(player.getUniqueId()), def);
                    });
            slot += 9; // one column per variant (slots 9, 18, 27, 36)
        }

        gui.setItem(49, ItemUtil.create(Material.ARROW, "<yellow>Back",
                List.of("<gray>Return to the spawner list.")),
                ev -> openTiers(player, CoreMC.getInstance().islands().api().getIsland(player.getUniqueId())));

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

    private SpawnerDef prevDef(final SpawnerDef def) {
        SpawnerDef prev = null;
        for (final SpawnerDef d : spawners.definitions()) {
            if (d.order() == def.order() - 1) {
                prev = d;
                break;
            }
        }
        return prev;
    }

    private static String prettyMat(final org.bukkit.Material m) {
        final String n = m.name().toLowerCase().replace("_", " ");
        return n.charAt(0) + n.substring(1);
    }

    @Override
    public List<String> onTabComplete(final org.bukkit.command.CommandSender sender,
                                      final org.bukkit.command.Command cmd, final String label,
                                      final String[] args) {
        return new ArrayList<>();
    }
}
