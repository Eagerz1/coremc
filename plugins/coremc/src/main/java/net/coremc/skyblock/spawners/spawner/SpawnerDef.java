package net.coremc.skyblock.spawners.spawner;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A spawner type definition from config.
 *
 * <p>Progression: each tier (except the first) requires killing {@code requirement}
 * of the PREVIOUS tier's mob to unlock. {@code order} defines the sequence (1 = first).
 * {@code interval} is the seconds between passive spawns.</p>
 *
 * <p>Each tier has exactly four <b>variants</b> (Normal, Corrupted, Ancient, Fourth) which
 * are increasingly valuable versions of the same mob. They differ only through economy
 * and progression — never through combat stats or custom models. Variant unlocks are
 * tracked separately from the tier unlock via the per-variant {@code requirement}.</p>
 */
public final class SpawnerDef {

    private final String id;
    private final String name;
    private final EntityType mob;
    private final double price;
    private final long interval; // seconds between spawns
    private final long requirement; // kills of previous-tier mob needed to unlock the tier
    private final int order; // progression position (1-based)
    private final Map<String, SpawnerVariant> variants = new LinkedHashMap<>();

    public SpawnerDef(final String id, final ConfigurationSection sec) {
        this.id = id;
        this.name = sec.getString("name", id);
        this.mob = EntityType.valueOf(sec.getString("mob", "PIG").toUpperCase(Locale.ROOT));
        this.price = sec.getDouble("price", 1000);
        this.interval = Math.max(1, sec.getLong("interval", 8));
        this.requirement = Math.max(0, sec.getLong("requirement", 0));
        this.order = sec.getInt("order", 1);

        final ConfigurationSection vsec = sec.getConfigurationSection("variants");
        if (vsec != null) {
            for (final String vid : vsec.getKeys(false)) {
                final ConfigurationSection v = vsec.getConfigurationSection(vid);
                if (v != null) {
                    variants.put(vid.toLowerCase(Locale.ROOT), SpawnerVariant.load(vid, v));
                }
            }
        }
        // Guarantee the four standard variants always exist (sensible defaults) so the
        // system is robust even if a tier's config omits a variant block. Multipliers
        // follow the CoreMC spec: Normal/Uncommon = 1x progression, Rare = 2x, Ancient = 3x.
        // The Ancient tier is the highest accelerator (never 4x).
        ensureVariant(SpawnerVariant.NORMAL, "Normal", 1, 0.0);
        ensureVariant(SpawnerVariant.UNCOMMON, "Uncommon", 1, this.price * 0.05);
        ensureVariant(SpawnerVariant.RARE, "Rare", 2, this.price * 0.2);
        ensureVariant(SpawnerVariant.ANCIENT, "Ancient", 3, this.price * 0.6);
    }

    private void ensureVariant(final String vid, final String fallbackName, final int fallbackMult, final double fallbackCost) {
        final SpawnerVariant existing = variants.get(vid);
        if (existing != null) {
            return;
        }
        variants.put(vid, new SpawnerVariant(vid, fallbackName, 0, fallbackCost, fallbackMult,
                0, 0, 0, 2, "", new java.util.ArrayList<>()));
    }

    public String id() { return id; }
    public String name() { return name; }
    public EntityType mob() { return mob; }
    public double price() { return price; }
    public long interval() { return interval; }
    public long requirement() { return requirement; }
    public int order() { return order; }

    public SpawnerVariant variant(final String vid) {
        return vid == null ? null : variants.get(vid.toLowerCase(Locale.ROOT));
    }

    public SpawnerVariant normalVariant() { return variants.get(SpawnerVariant.NORMAL); }

    /** The four variants in canonical order: Normal, Uncommon, Rare, Ancient. */
    public java.util.List<SpawnerVariant> variantsInOrder() {
        final java.util.List<SpawnerVariant> out = new java.util.ArrayList<>();
        add(out, SpawnerVariant.NORMAL);
        add(out, SpawnerVariant.UNCOMMON);
        add(out, SpawnerVariant.RARE);
        add(out, SpawnerVariant.ANCIENT);
        return out;
    }

    /** Alias used by the variant GUI. */
    public java.util.List<SpawnerVariant> variantList() { return variantsInOrder(); }

    private void add(final java.util.List<SpawnerVariant> out, final String vid) {
        final SpawnerVariant v = variants.get(vid);
        if (v != null) {
            out.add(v);
        }
    }
}
