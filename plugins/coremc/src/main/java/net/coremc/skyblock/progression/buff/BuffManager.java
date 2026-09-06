package net.coremc.skyblock.progression.buff;

import net.coremc.skyblock.progression.tree.TreeManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Island-wide buffs derived from island progression (island tree node levels and
 * island XP level). Buffs do not require a selected role. Each island tree node
 * with effect "member_limit", "island_size", "crop_regrowth", "generator_limit",
 * "spawner_limit", "beacon", "beacon_power", "island_home" contributes a buff.
 */
public final class BuffManager {

    private final JavaPlugin plugin;
    private final TreeManager trees;

    public BuffManager(final JavaPlugin plugin, final TreeManager trees) {
        this.plugin = plugin;
        this.trees = trees;
    }

    /** List of island buff rows for the GUI. */
    public List<BuffRow> rows(final int islandId) {
        final String key = String.valueOf(islandId);
        final List<BuffRow> out = new ArrayList<>();
        final TreeManager.TreeDef island = trees.tree("island");
        if (island == null) {
            return out;
        }
        for (final TreeManager.NodeDef n : island.nodes().values()) {
            final int lvl = trees.getLevel(key, "island", n.id());
            if (lvl <= 0) {
                continue;
            }
            out.add(new BuffRow(n.name(), lvl, effectLabel(n.effect()), buffDetail(n.id(), lvl)));
        }
        return out;
    }

    /** Generic multiplier for a buff effect id on an island (1.0 = none). */
    public double getMultiplier(final int islandId, final String effectId) {
        final String key = String.valueOf(islandId);
        final TreeManager.TreeDef island = trees.tree("island");
        if (island == null) {
            return 1.0;
        }
        double total = 1.0;
        for (final TreeManager.NodeDef n : island.nodes().values()) {
            if (!effectId.equalsIgnoreCase(n.effect())) {
                continue;
            }
            final int lvl = trees.getLevel(key, "island", n.id());
            total += lvl * 0.05;
        }
        return total;
    }

    private static String effectLabel(final String effect) {
        return switch (effect.toLowerCase(java.util.Locale.ROOT)) {
            case "member_limit" -> "Member capacity";
            case "island_size" -> "Island radius";
            case "crop_regrowth" -> "Crop regrowth";
            case "generator_limit" -> "Generator limit";
            case "spawner_limit" -> "Spawner limit";
            case "beacon" -> "Beacon";
            case "beacon_power" -> "Beacon power";
            case "island_home" -> "Extra homes";
            default -> "Bonus";
        };
    }

    private String buffDetail(final String nodeId, final int lvl) {
        return switch (nodeId) {
            case "member_limit_1", "member_limit_2", "member_limit_3", "member_limit_4", "member_limit_5" ->
                    "+1 member (total " + lvl + ")";
            case "island_size_1", "island_size_2", "island_size_3", "island_size_4", "island_size_5" ->
                    "+" + (lvl * 10) + " blocks radius";
            case "crop_regrowth_1", "crop_regrowth_2", "crop_regrowth_3", "crop_regrowth_4", "crop_regrowth_5" ->
                    "Regrowth tier " + lvl;
            case "generator_limit" -> "+5 generators";
            case "spawner_limit" -> "+5 spawners";
            case "island_home" -> "+1 home point";
            case "beacon" -> "Beacon active";
            case "beacon_power" -> "Stronger beacon";
            default -> "Level " + lvl;
        };
    }

    public record BuffRow(String name, int level, String label, String detail) {}
}
