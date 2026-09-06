package net.coremc.skyblock.gens.generator;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * A single generator type definition from config.
 *
 * <p>Generators produce normal farmable resources only (no ores / valuables),
 * generated on a configurable per-generator interval (default 3 seconds).</p>
 */
public final class GeneratorDef {

    private final String id;
    private final String name;
    private final Material material;
    private final List<Material> produce;
    private final int produceAmount;
    private final double price;
    private final long interval; // seconds between generations

    public GeneratorDef(final String id, final ConfigurationSection sec) {
        this.id = id;
        this.name = sec.getString("name", id);
        final Material m = Material.matchMaterial(sec.getString("material", "GOLD_BLOCK"));
        this.material = m != null ? m : Material.GOLD_BLOCK;
        this.produce = new ArrayList<>();
        final List<String> list = sec.getStringList("produce-list");
        final String single = sec.getString("produce", "");
        if (!list.isEmpty()) {
            for (final String s : list) {
                final Material p = Material.matchMaterial(s.toUpperCase(java.util.Locale.ROOT));
                if (p != null) {
                    this.produce.add(p);
                }
            }
        } else if (!single.isBlank()) {
            final Material p = Material.matchMaterial(single.toUpperCase(java.util.Locale.ROOT));
            if (p != null) {
                this.produce.add(p);
            }
        }
        this.produceAmount = Math.max(1, sec.getInt("produce-amount", 1));
        this.price = sec.getDouble("price", 1000);
        this.interval = Math.max(1, sec.getLong("interval", 3));
    }

    public String id() { return id; }
    public String name() { return name; }
    public Material material() { return material; }
    /** Items this generator produces (one entry per drop; mixed generators have several). */
    public List<Material> produce() { return produce; }
    public int produceAmount() { return produceAmount; }
    public double price() { return price; }
    public long interval() { return interval; }
}
