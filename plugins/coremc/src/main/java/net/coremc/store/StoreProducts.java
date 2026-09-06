package net.coremc.store;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Loaded store catalogue (keys / bundles / ranks) from separate config files. */
public final class StoreProducts {

    private final JavaPlugin plugin;
    private final FileConfiguration keys;
    private final FileConfiguration bundles;
    private final FileConfiguration ranks;

    public StoreProducts(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.keys = load("keys.yml");
        this.bundles = load("bundles.yml");
        this.ranks = load("ranks.yml");
    }

    private FileConfiguration load(final String name) {
        final File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) {
            plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(f);
    }

    public List<KeyDef> keys() {
        final List<KeyDef> out = new ArrayList<>();
        final ConfigurationSection root = keys.getConfigurationSection("keys");
        if (root == null) {
            return out;
        }
        for (final String id : root.getKeys(false)) {
            final ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            out.add(new KeyDef(
                    id,
                    s.getString("display-name", id),
                    s.getDouble("price", 0),
                    s.getString("crate", id),
                    s.getString("item-model", "coremc:" + id),
                    s.getString("color", "WHITE"),
                    s.getStringList("description")));
        }
        return out;
    }

    public List<BundleDef> bundles() {
        final List<BundleDef> out = new ArrayList<>();
        final ConfigurationSection root = bundles.getConfigurationSection("bundles");
        if (root == null) {
            return out;
        }
        for (final String id : root.getKeys(false)) {
            final ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            out.add(new BundleDef(
                    id,
                    s.getString("display-name", id),
                    s.getDouble("price", 0),
                    s.getString("item-model", "coremc:" + id),
                    s.getStringList("contents")));
        }
        return out;
    }

    public List<RankDef> ranks() {
        final List<RankDef> out = new ArrayList<>();
        final ConfigurationSection root = ranks.getConfigurationSection("ranks");
        if (root == null) {
            return out;
        }
        for (final String id : root.getKeys(false)) {
            final ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            out.add(new RankDef(
                    id,
                    s.getString("display-name", id),
                    s.getDouble("price", 0),
                    s.getString("dye", "WHITE"),
                    s.getStringList("perks"),
                    s.getStringList("commands")));
        }
        return out;
    }

    // ---- definitions ----
    public record KeyDef(String id, String name, double price, String crate, String model, String color, List<String> desc) {}
    public record BundleDef(String id, String name, double price, String model, List<String> contents) {}
    public record RankDef(String id, String name, double price, String dye, List<String> perks, List<String> commands) {}
}
