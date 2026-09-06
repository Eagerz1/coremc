package net.coremc.skyblock.progression.tree;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Upgrade tree registry. Trees are defined in trees.yml; purchased node levels
 * are persisted per "scope" (a player UUID for activity trees, an island id for
 * the island tree) in trees_data.yml.
 */
public final class TreeManager {

    public enum Scope { PLAYER, ISLAND }

    private final JavaPlugin plugin;
    private final File dataFile;
    private final YamlConfiguration data;

    /** Tree id -> definition. */
    private final java.util.Map<String, TreeDef> trees = new java.util.LinkedHashMap<>();

    public TreeManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "trees_data.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        load();
    }

    private void load() {
        final File f = new File(plugin.getDataFolder(), "trees.yml");
        if (!f.exists()) {
            plugin.saveResource("trees.yml", false);
        }
        final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        final ConfigurationSection root = cfg.getConfigurationSection("trees");
        if (root == null) {
            return;
        }
        for (final String tid : root.getKeys(false)) {
            final ConfigurationSection ts = root.getConfigurationSection(tid);
            if (ts == null) {
                continue;
            }
            final boolean island = "island".equalsIgnoreCase(ts.getString("type", "activity"));
            final TreeDef tree = new TreeDef(tid, ts.getString("name", tid),
                    island ? Scope.ISLAND : Scope.PLAYER);
            final ConfigurationSection nodes = ts.getConfigurationSection("nodes");
            if (nodes != null) {
                for (final String nid : nodes.getKeys(false)) {
                    final ConfigurationSection ns = nodes.getConfigurationSection(nid);
                    if (ns == null) {
                        continue;
                    }
                    final List<String> reqs = ns.getStringList("requires");
                    tree.nodes().put(nid, new NodeDef(
                            nid,
                            ns.getString("name", nid),
                            ns.getLong("cost", 0),
                            ns.getInt("max-level", 1),
                            reqs,
                            ns.getString("effect", ""),
                            ns.getString("description", "")));
                }
            }
            trees.put(tid, tree);
        }
    }

    public java.util.Set<String> treeIds() {
        return trees.keySet();
    }

    public TreeDef tree(final String id) {
        return trees.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    public List<TreeDef> trees() {
        return new ArrayList<>(trees.values());
    }

    public NodeDef node(final String treeId, final String nodeId) {
        final TreeDef t = tree(treeId);
        return t == null ? null : t.nodes().get(nodeId.toLowerCase(java.util.Locale.ROOT));
    }

    /** Purchased level for a scope key (uuid string or island id). */
    public int getLevel(final String scopeKey, final String treeId, final String nodeId) {
        return data.getInt("data." + scopeKey + "." + treeId + "." + nodeId, 0);
    }

    public int maxLevel(final String treeId, final String nodeId) {
        final NodeDef n = node(treeId, nodeId);
        return n == null ? 0 : n.maxLevel();
    }

    public boolean isMaxed(final String scopeKey, final String treeId, final String nodeId) {
        return getLevel(scopeKey, treeId, nodeId) >= maxLevel(treeId, nodeId);
    }

    /** Next-level cost, or -1 if undefined/maxed. */
    public long nextCost(final String treeId, final String nodeId) {
        final NodeDef n = node(treeId, nodeId);
        if (n == null || n.maxLevel() <= 0) {
            return -1;
        }
        return n.cost();
    }

    /** Whether all prerequisite nodes are owned (level >= 1). */
    public boolean requirementsMet(final String scopeKey, final String treeId, final String nodeId) {
        final NodeDef n = node(treeId, nodeId);
        if (n == null) {
            return false;
        }
        for (final String req : n.requires()) {
            if (getLevel(scopeKey, treeId, req) < 1) {
                return false;
            }
        }
        return true;
    }

    public List<String> unmetRequirements(final String scopeKey, final String treeId, final String nodeId) {
        final List<String> out = new ArrayList<>();
        final NodeDef n = node(treeId, nodeId);
        if (n == null) {
            return out;
        }
        for (final String req : n.requires()) {
            if (getLevel(scopeKey, treeId, req) < 1) {
                final NodeDef rn = node(treeId, req);
                out.add(rn == null ? req : rn.name());
            }
        }
        return out;
    }

    /** Attempt to purchase the next level. Returns true on success. */
    public boolean purchase(final String scopeKey, final String treeId, final String nodeId) {
        if (isMaxed(scopeKey, treeId, nodeId)) {
            return false;
        }
        if (!requirementsMet(scopeKey, treeId, nodeId)) {
            return false;
        }
        final long cost = nextCost(treeId, nodeId);
        if (cost < 0) {
            return false;
        }
        data.set("data." + scopeKey + "." + treeId + "." + nodeId,
                getLevel(scopeKey, treeId, nodeId) + 1);
        save();
        return true;
    }

    public void save() {
        try {
            data.save(dataFile);
        } catch (final java.io.IOException e) {
            plugin.getLogger().warning("Could not save trees_data.yml: " + e.getMessage());
        }
    }

    // ---- island shared XP (stored under the island scope as "_xp") ----
    public int getIslandXp(final int islandId) {
        return data.getInt("islandxp." + islandId, 0);
    }

    public void purchaseIslandXp(final int islandId, final int total) {
        data.set("islandxp." + islandId, total);
        save();
    }

    /** Wipe all progression for an island (tree nodes + island XP). Used on island delete/reset. */
    public void clearIsland(final int islandId) {
        data.set("data." + islandId, null);
        data.set("islandxp." + islandId, null);
        save();
    }

    // ---- definitions ----
    public static final class TreeDef {
        private final String id;
        private final String name;
        private final Scope scope;
        private final java.util.Map<String, NodeDef> nodes = new java.util.LinkedHashMap<>();

        public TreeDef(final String id, final String name, final Scope scope) {
            this.id = id;
            this.name = name;
            this.scope = scope;
        }
        public String id() { return id; }
        public String name() { return name; }
        public Scope scope() { return scope; }
        public java.util.Map<String, NodeDef> nodes() { return nodes; }
    }

    public static final class NodeDef {
        private final String id;
        private final String name;
        private final long cost;
        private final int maxLevel;
        private final List<String> requires;
        private final String effect;
        private final String description;

        public NodeDef(final String id, final String name, final long cost, final int maxLevel,
                       final List<String> requires, final String effect, final String description) {
            this.id = id;
            this.name = name;
            this.cost = cost;
            this.maxLevel = maxLevel;
            this.requires = requires;
            this.effect = effect;
            this.description = description;
        }
        public String id() { return id; }
        public String name() { return name; }
        public long cost() { return cost; }
        public int maxLevel() { return maxLevel; }
        public List<String> requires() { return requires; }
        public String effect() { return effect; }
        public String description() { return description; }
    }
}
