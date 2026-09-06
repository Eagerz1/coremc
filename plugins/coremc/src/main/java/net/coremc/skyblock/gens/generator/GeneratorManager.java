package net.coremc.skyblock.gens.generator;
import net.coremc.coremc.CoreMC;

import net.coremc.foundation.util.FormatUtil;
import net.coremc.foundation.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

/**
 * Manages generator definitions, placed generators, purchase and per-generator
 * production ticks.
 *
 * <p>The island generator cap is global (not per-type): {@code base cap +
 * generator_limit upgrade levels * per-level}, hard-clamped to a maximum that can
 * never exceed {@code gens.max-cap} (default 5000). The cap is enforced when
 * buying and when placing.</p>
 */
public final class GeneratorManager {

    private final JavaPlugin plugin;
    private final Map<String, GeneratorDef> defs = new HashMap<>();
    private final Map<String, PlacedGenerator> placed = new HashMap<>(); // key: world:x:y:z
    private final Map<String, ArmorStand> holograms = new HashMap<>(); // key -> floating stack label
    private final File placedFile;
    private final YamlConfiguration placedData;

    public GeneratorManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        final ConfigurationSection sec = plugin.getConfig().getConfigurationSection("gens.generators");
        if (sec != null) {
            for (final String id : sec.getKeys(false)) {
                defs.put(id, new GeneratorDef(id, sec.getConfigurationSection(id)));
            }
        }
        this.placedFile = new File(plugin.getDataFolder(), "gens_placed.yml");
        this.placedData = YamlConfiguration.loadConfiguration(placedFile);
        loadPlaced();
        startTicker();
    }

    // ---- definitions ----

    public List<GeneratorDef> definitions() {
        return new ArrayList<>(defs.values());
    }

    public GeneratorDef get(final String id) {
        return defs.get(id);
    }

    // ---- island generator cap ----

    public int baseCap() {
        return plugin.getConfig().getInt("gens.default-cap", 10);
    }

    public int maxCap() {
        return plugin.getConfig().getInt("gens.max-cap", 5000);
    }

    /** Max generators stacked into a single block (default 128). */
    public int stackMax() {
        return Math.max(1, plugin.getConfig().getInt("gens.stack-max", 128));
    }

    /** Global cap for an island = base + upgrade levels * per-level, clamped to max-cap. */
    public int capFor(final int islandId) {
        final int base = baseCap();
        int levels = 0;
        final var prog = net.coremc.coremc.CoreMC.getInstance().progression();
        if (prog != null) {
            levels = prog.trees().getLevel(String.valueOf(islandId), "island", "generator_limit");
        }
        final int perLevel = plugin.getConfig().getInt("gens.cap-per-upgrade-level", 50);
        final int cap = base + levels * perLevel;
        return Math.min(cap, maxCap());
    }

    /** Total placed generators on an island. */
    public int totalFor(final int islandId) {
        int c = 0;
        for (final PlacedGenerator g : placed.values()) {
            if (g.islandId() == islandId) {
                c++;
            }
        }
        return c;
    }

    /** How many of a type an island already has placed. */
    public int countFor(final int islandId, final String genId) {
        int c = 0;
        for (final PlacedGenerator g : placed.values()) {
            if (g.islandId() == islandId && g.genId().equals(genId)) {
                c++;
            }
        }
        return c;
    }

    /** Give a player the generator item for placement. Returns false if over island cap. */
    public boolean give(final Player player, final String genId) {
        final var island = CoreMC.getInstance().islands().api().getIsland(player.getUniqueId());
        if (island == null) {
            return false;
        }
        final GeneratorDef def = get(genId);
        if (def == null) {
            return false;
        }
        if (totalFor(island.getId()) >= capFor(island.getId())) {
            return false;
        }
        player.getInventory().addItem(makeItem(def));
        return true;
    }

    public ItemStack makeItem(final GeneratorDef def) {
        final List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Produces:");
        for (final Material m : def.produce()) {
            lore.add("<green>• <white>" + human(m) + (def.produce().size() > 1 ? " x" + def.produceAmount() : ""));
        }
        if (def.produce().size() == 1) {
            lore.add("<gray>Amount: <white>x" + def.produceAmount());
        }
        lore.add("");
        lore.add("<gray>Generation Time:");
        lore.add("<aqua>• <white>" + def.interval() + (def.interval() == 1 ? " second" : " seconds"));
        lore.add("");
        lore.add("<gray>Price:");
        lore.add("<yellow>• <white>$" + FormatUtil.formatNumber((long) def.price()));
        lore.add("");
        lore.add("<gray>Status: <green>Ready to place");
        lore.add("<gray>• Right-click to place / stack");
        lore.add("<gray>• Shift + right-click stacks onto same gen (max " + stackMax() + ")");
        lore.add("<gray>• Shift + left-click picks up the whole pile");
        final ItemStack item = ItemUtil.create(def.material(), "<bold><gold>" + def.name(), lore);
        final var meta = item.getItemMeta();
        if (meta != null) {
            // Generators stack like spawners (max 64) so players can carry many at once.
            try { meta.setMaxStackSize(64); } catch (final Throwable ignored) {}
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "cmc_gen"),
                    org.bukkit.persistence.PersistentDataType.STRING, def.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Place a generator: sets a real, persistent GENERATOR block in the world and
     * records it. The physical block means it survives restarts (re-applied on
     * load) and is visible/pick-up-able on the island.
     *
     * <p>If a generator of the same type is ALREADY in this block, the new one is
     * stacked on top of it (count + 1) instead of placing a second block — up to
     * {@link #stackMax()}. Returns the resulting placed record, or null if the pile
     * is already at cap.</p>
     */
    public PlacedGenerator place(final Block block, final int islandId, final String genId) {
        final String k = key(block.getLocation());
        final PlacedGenerator existing = placed.get(k);
        if (existing != null && existing.genId().equals(genId)) {
            return addTo(block.getLocation(), islandId, genId);
        }
        final GeneratorDef def = get(genId);
        final Material type = def != null ? def.material() : Material.GOLD_BLOCK;
        block.setType(type);
        final PlacedGenerator rec = new PlacedGenerator(k, islandId, genId, 1);
        placed.put(k, rec);
        placedData.set(k + ".island", islandId);
        placedData.set(k + ".gen", genId);
        placedData.set(k + ".count", 1);
        placedData.set(k + ".material", type.name());
        savePlaced();
        spawnHologram(block.getLocation(), def, 1);
        // Notify listeners (e.g. the missions system) that a generator was placed.
        try {
            final GeneratorPlacedEvent ev = new GeneratorPlacedEvent(block, islandId, genId);
            plugin.getServer().getPluginManager().callEvent(ev);
        } catch (final Throwable ignored) {}
        return rec;
    }

    /**
     * Add one generator to the pile at {@code loc} (same type already present). Caps at
     * {@link #stackMax()}. Returns the updated record, or null if already full.
     */
    public PlacedGenerator addTo(final Location loc, final int islandId, final String genId) {
        final String k = key(loc);
        final PlacedGenerator existing = placed.get(k);
        if (existing == null || !existing.genId().equals(genId)) {
            return null;
        }
        final int max = stackMax();
        if (existing.count() >= max) {
            return null; // pile full
        }
        final int next = existing.count() + 1;
        final PlacedGenerator rec = existing.withCount(next);
        placed.put(k, rec);
        placedData.set(k + ".count", next);
        savePlaced();
        updateHologram(loc, get(genId), next);
        return rec;
    }

    /** Current stack size at a location (1 if present, 0 if none). */
    public int countAt(final Location loc) {
        final PlacedGenerator g = placed.get(key(loc));
        return g == null ? 0 : g.count();
    }

    /**
     * Pick up the generator pile at the clicked block's location (left-click). Removes
     * the world block, the record and the hologram, and returns the WHOLE pile to the
     * player as generator items (stacked 64-per-item). The island count is derived from
     * the live placed map, so it decrements automatically.
     */
    public boolean pickup(final Location loc, final Player player) {
        final String k = key(loc);
        final PlacedGenerator g = placed.get(k);
        if (g == null) {
            return false;
        }
        final GeneratorDef def = get(g.genId());
        if (def == null) {
            return false;
        }
        // Remove the physical block + hologram.
        final Block block = loc.getBlock();
        block.setType(Material.AIR);
        removeHologram(loc);
        placed.remove(k);
        placedData.set(k, null);
        savePlaced();
        // Return every generator in the pile as items (max 64 each).
        final int total = g.count();
        int remaining = total;
        while (remaining > 0) {
            final int amt = Math.min(64, remaining);
            final ItemStack item = makeItem(def);
            item.setAmount(amt);
            final java.util.Map<Integer, ItemStack> left = player.getInventory().addItem(item);
            if (!left.isEmpty()) {
                for (final ItemStack drop : left.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
            remaining -= amt;
        }
        return true;
    }

    /**
     * Remove every generator belonging to an island: deletes the in-memory records,
     * clears the physical blocks, and removes the persisted entries. Used when an
     * island is deleted/reset so its generators don't linger in the world.
     */
    public void clearIsland(final int islandId) {
        final java.util.List<String> toRemove = new java.util.ArrayList<>();
        for (final var entry : placed.entrySet()) {
            if (entry.getValue().islandId() == islandId) {
                toRemove.add(entry.getKey());
            }
        }
        for (final String k : toRemove) {
            final Location loc = locFromKey(k);
            if (loc != null && loc.getWorld() != null) {
                loc.getBlock().setType(Material.AIR);
            }
            removeHologram(loc);
            placed.remove(k);
            placedData.set(k, null);
        }
        if (!toRemove.isEmpty()) {
            savePlaced();
        }
    }

    public PlacedGenerator getAt(final Location loc) {
        return placed.get(key(loc));
    }

    private void loadPlaced() {
        for (final String k : placedData.getKeys(false)) {
            final int island = placedData.getInt(k + ".island");
            final String gen = placedData.getString(k + ".gen");
            if (gen != null) {
                final int count = Math.max(1, placedData.getInt(k + ".count", 1));
                placed.put(k, new PlacedGenerator(k, island, gen, count));
                // Restore the physical block so the generator is visible after a restart.
                final Location loc = locFromKey(k);
                final GeneratorDef def = get(gen);
                if (loc != null) {
                    final Material type = def != null ? def.material() : Material.GOLD_BLOCK;
                    loc.getBlock().setType(type);
                    // Re-create the floating stack label for any pile larger than one.
                    if (count > 1) {
                        spawnHologram(loc, def, count);
                    }
                }
            }
        }
    }

    private void savePlaced() {
        try {
            placedData.save(placedFile);
        } catch (final java.io.IOException e) {
            plugin.getLogger().warning("Could not save gens_placed.yml: " + e.getMessage());
        }
    }

    // ---- floating stack holograms (invisible ArmorStand above the block) ----

    /** Namespaced key marking a hologram ArmorStand so we can find/clean it up. */
    private static final org.bukkit.NamespacedKey HOLO_KEY =
            new org.bukkit.NamespacedKey("coremc", "cmc_gen_holo");

    private String holoText(final GeneratorDef def, final int count) {
        final String name = def == null ? "Generator" : def.name();
        return "<bold><gold>" + count + "x " + name + "</gold></bold>";
    }

    private void spawnHologram(final Location blockLoc, final GeneratorDef def, final int count) {
        final Location stand = blockLoc.clone().add(0.5, 1.25, 0.5);
        final ArmorStand as = (ArmorStand) blockLoc.getWorld().spawnEntity(stand, EntityType.ARMOR_STAND);
        as.setVisible(false);
        as.setGravity(false);
        as.setInvulnerable(true);
        as.setMarker(true);
        as.setCustomNameVisible(true);
        as.customName(net.coremc.foundation.util.ColorUtil.parse(holoText(def, count)));
        as.getPersistentDataContainer().set(HOLO_KEY, PersistentDataType.STRING, "1");
        holograms.put(key(blockLoc), as);
    }

    private void updateHologram(final Location blockLoc, final GeneratorDef def, final int count) {
        final String k = key(blockLoc);
        ArmorStand as = holograms.get(k);
        if (as == null || !as.isValid()) {
            spawnHologram(blockLoc, def, count);
            return;
        }
        as.customName(net.coremc.foundation.util.ColorUtil.parse(holoText(def, count)));
    }

    private void removeHologram(final Location blockLoc) {
        final String k = key(blockLoc);
        final ArmorStand as = holograms.remove(k);
        if (as != null && as.isValid()) {
            as.remove();
        }
    }

    /** Remove all holograms + cancel tasks (called on plugin disable). */
    public void shutdown() {
        for (final ArmorStand as : holograms.values()) {
            if (as.isValid()) as.remove();
        }
        holograms.clear();
    }

    private void startTicker() {
        // One task every second; each placed generator only acts when its interval has elapsed.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            final long now = System.currentTimeMillis() / 1000L;
            for (final PlacedGenerator g : placed.values()) {
                final GeneratorDef def = get(g.genId());
                if (def == null || def.produce().isEmpty()) {
                    continue;
                }
                final var island = CoreMC.getInstance().islands().api().getIsland(g.islandId());
                if (island == null) {
                    continue;
                }
                // Prosperity set: generators run 1.2x faster for the island owner.
                final Player owner = Bukkit.getPlayer(island.getOwner());
                final double genMult = (owner != null)
                        ? CoreMC.getInstance().crates().progressionArmour().prosperityGeneratorMultiplier(owner)
                        : 1.0;
                final long effectiveInterval = Math.max(1, (long) Math.ceil(def.interval() / genMult));
                if (now % effectiveInterval != 0) {
                    continue;
                }
                final Location loc = locFromKey(g.key());
                if (loc == null || loc.getWorld() == null) {
                    continue;
                }
                final int count = g.count();
                // Drop the produced items on top of the generator, scaled by the pile size
                // (a 52x pile yields 52x output, split into stacks of 64 per item entity).
                final Location drop = loc.clone().add(0.5, 1.2, 0.5);
                for (final Material m : def.produce()) {
                    int remaining = def.produceAmount() * count;
                    while (remaining > 0) {
                        final int amt = Math.min(64, remaining);
                        final ItemStack stack = new ItemStack(m, amt);
                        final Item item = loc.getWorld().dropItemNaturally(drop, stack);
                        item.setCanMobPickup(false);
                        remaining -= amt;
                    }
                }
                // Contribute to island progression + tokens, scaled by the pile size.
                CoreMC.getInstance().islands().api().addStatistic(g.islandId(), "gens-earned", count);
                CoreMC.getInstance().islands().api().addXp(g.islandId(), count);
                final var prog = net.coremc.coremc.CoreMC.getInstance().progression();
                if (prog != null) {
                    final double tokenMult = (owner != null)
                            ? CoreMC.getInstance().crates().progressionArmour().prosperityTokenMultiplier(owner)
                            : 1.0;
                    prog.api().awardTokens(island.getOwner(), Math.max(1, (long) (count * tokenMult)));
                }
            }
        }, 20L, 20L);
    }

    private Location locFromKey(final String k) {
        final String[] p = k.split(":");
        if (p.length < 4) {
            return null;
        }
        final World w = Bukkit.getWorld(p[0]);
        if (w == null) {
            return null;
        }
        return new Location(w, Double.parseDouble(p[1]) + 0.5, Double.parseDouble(p[2]) + 1,
                Double.parseDouble(p[3]) + 0.5);
    }

    static String key(final Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    private static String human(final Material m) {
        final String n = m.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return java.util.Arrays.stream(n.split(" "))
                .map(s -> s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .reduce((a, b) -> a + " " + b)
                .orElse(n);
    }
}
