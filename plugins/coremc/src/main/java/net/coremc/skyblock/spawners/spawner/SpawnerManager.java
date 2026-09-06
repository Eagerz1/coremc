package net.coremc.skyblock.spawners.spawner;
import net.coremc.coremc.CoreMC;

import net.coremc.foundation.util.Economy;
import net.coremc.foundation.util.FormatUtil;
import net.coremc.foundation.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

/**
 * Manages spawner definitions, placed spawners, purchase, passive spawning and the
 * kill-based progression unlock chain.
 *
 * <p>Progression is per-island: each tier (except the first) unlocks once the
 * island has killed {@code requirement} of the previous tier's mob. Kill counts are
 * tracked from spawner-spawned mobs and persisted. The island spawner cap is global
 * (base + spawner_limit upgrade), hard-clamped to a maximum that never exceeds
 * {@code spawners.max-cap} (default 5000).</p>
 */
public final class SpawnerManager {

    private final JavaPlugin plugin;
    private final Map<String, SpawnerDef> defs = new LinkedHashMap<>();
    private final Map<String, PlacedSpawner> placed = new HashMap<>(); // key: world:x:y:z
    private final Map<String, ArmorStand> holograms = new HashMap<>(); // key -> floating stack label
    private final Map<String, String> prevMobOf = new HashMap<>(); // spawnerId -> previous spawnerId
    private final File placedFile;
    private final YamlConfiguration placedData;
    private final File progressFile;
    private final YamlConfiguration progressData;

    public SpawnerManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        final ConfigurationSection sec = plugin.getConfig().getConfigurationSection("spawners.spawners");
        if (sec != null) {
            final List<SpawnerDef> all = new ArrayList<>();
            for (final String id : sec.getKeys(false)) {
                final SpawnerDef d = new SpawnerDef(id, sec.getConfigurationSection(id));
                defs.put(id, d);
                all.add(d);
            }
            all.sort(Comparator.comparingInt(SpawnerDef::order));
            for (int i = 1; i < all.size(); i++) {
                prevMobOf.put(all.get(i).id(), all.get(i - 1).id());
            }
        }
        this.placedFile = new File(plugin.getDataFolder(), "spawners_placed.yml");
        this.placedData = YamlConfiguration.loadConfiguration(placedFile);
        this.progressFile = new File(plugin.getDataFolder(), "spawner_progress.yml");
        this.progressData = YamlConfiguration.loadConfiguration(progressFile);
        loadPlaced();
        startTicker();
    }

    // ---- definitions ----

    public List<SpawnerDef> definitions() {
        final List<SpawnerDef> list = new ArrayList<>(defs.values());
        list.sort(Comparator.comparingInt(SpawnerDef::order));
        return list;
    }

    public SpawnerDef get(final String id) {
        return defs.get(id);
    }

    // ---- island spawner cap ----

    public int baseCap() {
        return plugin.getConfig().getInt("spawners.default-cap", 10);
    }

    public int maxCap() {
        return plugin.getConfig().getInt("spawners.max-cap", 5000);
    }

    /**
     * Max spawners stacked into a single block. Defaults to the number nearest to 1000
     * that is divisible by 64 (15*64=960, 16*64=1024 -> 1024 is closer to 1000).
     */
    public int pileMax() {
        final int configured = plugin.getConfig().getInt("spawners.pile-max", -1);
        if (configured > 0) {
            return configured;
        }
        final int nearest = (int) Math.round(1000.0 / 64.0) * 64; // 1024
        return Math.max(1, nearest);
    }

    public int capFor(final int islandId) {
        final int base = baseCap();
        int levels = 0;
        final var prog = net.coremc.coremc.CoreMC.getInstance().progression();
        if (prog != null) {
            levels = prog.trees().getLevel(String.valueOf(islandId), "island", "spawner_limit");
        }
        final int perLevel = plugin.getConfig().getInt("spawners.cap-per-upgrade-level", 50);
        return Math.min(base + levels * perLevel, maxCap());
    }

    public int totalFor(final int islandId) {
        int c = 0;
        for (final PlacedSpawner s : placed.values()) {
            if (s.islandId() == islandId) {
                c++;
            }
        }
        return c;
    }

    public int countFor(final int islandId, final String spawnerId) {
        int c = 0;
        for (final PlacedSpawner s : placed.values()) {
            if (s.islandId() == islandId && s.spawnerId().equals(spawnerId)) {
                c++;
            }
        }
        return c;
    }

    // ---- progression ----

    /** Total kills of a mob type recorded for an island (from spawner-spawned mobs). */
    public long killsFor(final int islandId, final EntityType mob) {
        return progressData.getLong("kills." + islandId + "." + mob.name(), 0);
    }

    public void addKill(final int islandId, final EntityType mob, final long progression) {
        final String k = "kills." + islandId + "." + mob.name();
        progressData.set(k, killsFor(islandId, mob) + progression);
        saveProgress();
    }

    /** Backwards-compatible addKill (1 progression per kill). */
    public void addKill(final int islandId, final EntityType mob) {
        addKill(islandId, mob, 1);
    }

    /** Whether a spawner tier is unlocked for an island. */
    public boolean isUnlocked(final int islandId, final SpawnerDef def) {
        final String prev = prevMobOf.get(def.id());
        if (prev == null) {
            return true; // first tier always unlocked
        }
        final SpawnerDef prevDef = defs.get(prev);
        if (prevDef == null) {
            return true;
        }
        return killsFor(islandId, prevDef.mob()) >= def.requirement();
    }

    /**
     * Whether a specific variant of a (unlocked) spawner is available to the island.
     *
     * <p>A variant unlocks once the island's progression on the parent mob reaches
     * the variant's {@code requirement}. Higher variants are pure accelerators:
     * a player can reach a variant's requirement using ANY combination of variants
     * (e.g. 30 normal kills + 10 ancient kills = 30 + 30 = 60 progression), so they
     * never have to grind every lower variant sequentially.</p>
     */
    public boolean isVariantUnlocked(final int islandId, final SpawnerDef def, final SpawnerVariant variant) {
        return killsFor(islandId, def.mob()) >= variant.requirement();
    }

    /** Progression (kills) this island has accumulated toward the given mob's variants. */
    public long variantProgress(final int islandId, final SpawnerDef def) {
        return killsFor(islandId, def.mob());
    }

    // ---- purchase / placement ----

    public boolean give(final Player player, final String spawnerId) {
        return give(player, spawnerId, "normal");
    }

    /** Give a spawner item for a specific variant (charges the variant unlock cost once). */
    public boolean give(final Player player, final String spawnerId, final String variantId) {
        final var island = CoreMC.getInstance().islands().api().getIsland(player.getUniqueId());
        if (island == null) {
            return false;
        }
        final SpawnerDef def = get(spawnerId);
        if (def == null) {
            return false;
        }
        if (!isUnlocked(island.getId(), def)) {
            return false;
        }
        final SpawnerVariant v = def.variant(variantId);
        if (v == null || !isVariantUnlocked(island.getId(), def, v)) {
            return false;
        }
        if (totalFor(island.getId()) >= capFor(island.getId())) {
            return false;
        }
        // Charge the variant unlock cost exactly once per island (idempotent with the
        // right-click placed-spawner GUI, which marks the same "variant_bought" stat).
        if (v.cost() > 0 && !alreadyBought(island.getId(), spawnerId, variantId)) {
            final Economy econ = new Economy(plugin);
            if (!econ.charge(player.getUniqueId(), v.cost(), "spawner-variant:" + spawnerId + ":" + variantId)) {
                return false;
            }
            markBought(island.getId(), spawnerId, variantId);
        }
        player.getInventory().addItem(makeItem(def, variantId));
        return true;
    }

    private boolean alreadyBought(final int islandId, final String spawnerId, final String variantId) {
        return CoreMC.getInstance().islands().api()
                .getStatistic(islandId, "variant_bought." + spawnerId + "." + variantId) >= 1;
    }

    private void markBought(final int islandId, final String spawnerId, final String variantId) {
        CoreMC.getInstance().islands().api()
                .addStatistic(islandId, "variant_bought." + spawnerId + "." + variantId, 1);
    }

    /** Namespaced key carrying the variant id on a spawner item / placed mob. */
    public static final org.bukkit.NamespacedKey VARIANT_KEY =
            new org.bukkit.NamespacedKey("coremc", "cmc_variant");

    public ItemStack makeItem(final SpawnerDef def) {
        return makeItem(def, "normal");
    }

    public ItemStack makeItem(final SpawnerDef def, final String variantId) {
        final SpawnerVariant v = def.variant(variantId);
        final List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Mob:");
        lore.add("<red>• <white>" + prettyMob(v.effectiveMob(def.mob())));
        lore.add("");
        lore.add("<gray>Variant:");
        lore.add("<yellow>• <white>" + v.displayName());
        lore.add("");
        lore.add("<gray>Required Kills:");
        lore.add("<yellow>• <white>" + FormatUtil.formatNumber(def.requirement()) + " kills");
        lore.add("");
        lore.add("<gray>Status: <green>UNLOCKED");
        lore.add("<gray>• Right-click to place / stack");
        lore.add("<gray>• Shift + right-click stacks onto same spawner (max " + pileMax() + ")");
        lore.add("<gray>• Right-click a placed spawner to open its menu");
        lore.add("<gray>• Shift + left-click picks up the whole pile");
        final ItemStack item = ItemUtil.create(Material.SPAWNER, "<bold><gold>" + def.name(), lore);
        final var meta = item.getItemMeta();
        if (meta != null) {
            // Spawners stack to 64 so players can carry a whole pile at once.
            try { meta.setMaxStackSize(64); } catch (final Throwable ignored) {}
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "cmc_spawner"),
                    org.bukkit.persistence.PersistentDataType.STRING, def.id());
            meta.getPersistentDataContainer().set(VARIANT_KEY,
                    org.bukkit.persistence.PersistentDataType.STRING, variantId);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Place a spawner: sets a real, persistent SPAWNER block in the world (with the
     * correct mob type for the variant) and records it. The physical block survives
     * restarts (re-applied on load) and is visible/pick-up-able on the island.
     *
     * <p>If a spawner of the same type + variant is ALREADY in this block, the new one
     * is stacked on top of it (count + 1) instead of placing a second block — up to
     * {@link #pileMax()}. Returns the resulting record, or null if the pile is full.</p>
     */
    public PlacedSpawner place(final Block block, final int islandId, final String spawnerId, final String variantId) {
        final String k = key(block.getLocation());
        final String vId = variantId == null ? "normal" : variantId;
        final PlacedSpawner existing = placed.get(k);
        if (existing != null && existing.spawnerId().equals(spawnerId) && existing.variant().equals(vId)) {
            return addTo(block.getLocation(), islandId, spawnerId, vId);
        }
        block.setType(Material.SPAWNER);
        final var state = block.getState();
        final SpawnerDef def = get(spawnerId);
        if (state instanceof org.bukkit.block.CreatureSpawner cs && def != null) {
            cs.setSpawnedType(def.variant(vId).effectiveMob(def.mob()));
            cs.update();
        }
        final PlacedSpawner rec = new PlacedSpawner(k, islandId, spawnerId, 1, vId);
        placed.put(k, rec);
        placedData.set(k + ".island", islandId);
        placedData.set(k + ".spawner", spawnerId);
        placedData.set(k + ".variant", vId);
        placedData.set(k + ".count", 1);
        savePlaced();
        spawnHologram(block.getLocation(), def, 1, vId);
        // Notify listeners (e.g. the missions system) that a spawner was placed.
        try {
            final SpawnerPlacedEvent ev = new SpawnerPlacedEvent(block, islandId, spawnerId);
            plugin.getServer().getPluginManager().callEvent(ev);
        } catch (final Throwable ignored) {}
        return rec;
    }

    /** Backwards-compatible placement with the {@code normal} variant. */
    public PlacedSpawner place(final Block block, final int islandId, final String spawnerId) {
        return place(block, islandId, spawnerId, "normal");
    }

    /**
     * Add one spawner to the pile at {@code loc} (same type + variant already present).
     * Caps at {@link #pileMax()}. Returns the updated record, or null if already full.
     */
    public PlacedSpawner addTo(final Location loc, final int islandId, final String spawnerId, final String variantId) {
        final String k = key(loc);
        final PlacedSpawner existing = placed.get(k);
        if (existing == null || !existing.spawnerId().equals(spawnerId) || !existing.variant().equals(variantId)) {
            return null;
        }
        final int max = pileMax();
        if (existing.count() >= max) {
            return null; // pile full
        }
        final int next = existing.count() + 1;
        final PlacedSpawner rec = existing.withCount(next);
        placed.put(k, rec);
        placedData.set(k + ".count", next);
        savePlaced();
        updateHologram(loc, get(spawnerId), next, variantId);
        return rec;
    }

    /** Backwards-compatible add (assumes the existing record's variant). */
    public PlacedSpawner addTo(final Location loc, final int islandId, final String spawnerId) {
        final PlacedSpawner existing = placed.get(key(loc));
        return existing == null ? null : addTo(loc, islandId, spawnerId, existing.variant());
    }

    /** Current stack size at a location (1 if present, 0 if none). */
    public int countAt(final Location loc) {
        final PlacedSpawner s = placed.get(key(loc));
        return s == null ? 0 : s.count();
    }

    /**
     * Pick up the spawner pile at the clicked block's location (left-click). Removes
     * the world block, the record and the hologram, and returns the WHOLE pile to the
     * player as spawner items (stacked 64-per-item). The island count is derived from
     * the live placed map, so it decrements automatically.
     */
    public boolean pickup(final Location loc, final Player player) {
        final String k = key(loc);
        final PlacedSpawner s = placed.get(k);
        if (s == null) {
            return false;
        }
        final SpawnerDef def = get(s.spawnerId());
        if (def == null) {
            return false;
        }
        final Block block = loc.getBlock();
        block.setType(Material.AIR);
        removeHologram(loc);
        placed.remove(k);
        placedData.set(k, null);
        savePlaced();
        // Return every spawner in the pile as items (max 64 each).
        int remaining = s.count();
        while (remaining > 0) {
            final int amt = Math.min(64, remaining);
            final ItemStack item = makeItem(def, s.variant());
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
     * Remove every spawner belonging to an island: deletes the in-memory records,
     * clears the physical SPAWNER blocks, removes persisted entries, and kills any
     * stacked mobs that were spawned by this island's spawners. Used when an island
     * is deleted/reset so its spawners and mobs don't linger in the world.
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
                // Kill any stacked mobs standing on this spawner.
                for (final org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, 2.0, 2.0, 2.0)) {
                    if (e instanceof org.bukkit.entity.LivingEntity le
                            && le.getPersistentDataContainer().has(STACK_KEY, PersistentDataType.INTEGER)) {
                        le.remove();
                    }
                }
            }
            removeHologram(loc);
            placed.remove(k);
            placedData.set(k, null);
        }
        if (!toRemove.isEmpty()) {
            savePlaced();
        }
        // Also clear any stacked mobs elsewhere on the island that carry this island's tag.
        final String tag = islandId + ":";
        for (final org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
            for (final org.bukkit.entity.Entity e : w.getEntities()) {
                if (!(e instanceof org.bukkit.entity.LivingEntity le)) {
                    continue;
                }
                final String src = le.getPersistentDataContainer().get(SPAWN_TAG_KEY, PersistentDataType.STRING);
                if (src != null && src.contains(tag) && le.getPersistentDataContainer()
                        .has(STACK_KEY, PersistentDataType.INTEGER)) {
                    le.remove();
                }
            }
        }
        // Wipe this island's kill-progress so unlock chains reset cleanly.
        final org.bukkit.configuration.ConfigurationSection killsRoot = progressData.getConfigurationSection("kills." + islandId);
        if (killsRoot != null) {
            for (final String mob : killsRoot.getKeys(false)) {
                progressData.set("kills." + islandId + "." + mob, null);
            }
            saveProgress();
        }
    }

    public PlacedSpawner getAt(final Location loc) {
        return placed.get(key(loc));
    }

    /**
     * Change the variant produced by a placed spawner. Updates the in-memory record,
     * the persisted entry, the physical mob type and the hologram. Returns the
     * updated record, or null if no spawner is at that location.
     */
    public PlacedSpawner setVariant(final Location loc, final String variantId) {
        final String k = key(loc);
        final PlacedSpawner s = placed.get(k);
        if (s == null) {
            return null;
        }
        final SpawnerDef def = get(s.spawnerId());
        final String vId = variantId == null ? "normal" : variantId;
        final PlacedSpawner rec = s.withVariant(vId);
        placed.put(k, rec);
        placedData.set(k + ".variant", vId);
        savePlaced();
        // Update the block's mob type for this variant.
        final Block block = loc.getBlock();
        if (block.getType() == Material.SPAWNER && def != null) {
            final var state = block.getState();
            if (state instanceof org.bukkit.block.CreatureSpawner cs) {
                cs.setSpawnedType(def.variant(vId).effectiveMob(def.mob()));
                cs.update();
            }
        }
        updateHologram(loc, def, s.count(), vId);
        return rec;
    }

    private void loadPlaced() {
        for (final String k : placedData.getKeys(false)) {
            final int island = placedData.getInt(k + ".island");
            final String sp = placedData.getString(k + ".spawner");
            if (sp != null) {
                final int count = Math.max(1, placedData.getInt(k + ".count", 1));
                final String variant = placedData.getString(k + ".variant", "normal");
                placed.put(k, new PlacedSpawner(k, island, sp, count, variant));
                // Restore the physical SPAWNER block (with its mob type for the variant) after a restart.
                final Location loc = locFromKey(k);
                final SpawnerDef def = get(sp);
                if (loc != null) {
                    loc.getBlock().setType(Material.SPAWNER);
                    final var state = loc.getBlock().getState();
                    if (state instanceof org.bukkit.block.CreatureSpawner cs && def != null) {
                        cs.setSpawnedType(def.variant(variant).effectiveMob(def.mob()));
                        cs.update();
                    }
                    // Re-create the floating stack label for any pile larger than one.
                    if (count > 1) {
                        spawnHologram(loc, def, count, variant);
                    }
                }
            }
        }
    }

    private void savePlaced() {
        try {
            placedData.save(placedFile);
        } catch (final java.io.IOException e) {
            plugin.getLogger().warning("Could not save spawners_placed.yml: " + e.getMessage());
        }
    }

    private void saveProgress() {
        try {
            progressData.save(progressFile);
        } catch (final java.io.IOException e) {
            plugin.getLogger().warning("Could not save spawner_progress.yml: " + e.getMessage());
        }
    }

    // ---- floating stack holograms (invisible ArmorStand above the block) ----

    /** Namespaced key marking a hologram ArmorStand so we can find/clean it up. */
    private static final org.bukkit.NamespacedKey HOLO_KEY =
            new org.bukkit.NamespacedKey("coremc", "cmc_spawner_holo");

    private String holoText(final SpawnerDef def, final int count, final String variant) {
        final String name = def == null ? "Spawner" : def.name();
        final SpawnerVariant v = def == null ? null : def.variant(variant);
        final String tag = v == null ? "" : " <gold>[" + v.displayName() + "]</gold>";
        return "<bold><gold>" + count + "x " + name + tag + "</gold></bold>";
    }

    private void spawnHologram(final Location blockLoc, final SpawnerDef def, final int count, final String variant) {
        final Location stand = blockLoc.clone().add(0.5, 1.25, 0.5);
        final ArmorStand as = (ArmorStand) blockLoc.getWorld().spawnEntity(stand, EntityType.ARMOR_STAND);
        as.setVisible(false);
        as.setGravity(false);
        as.setInvulnerable(true);
        as.setMarker(true);
        as.setCustomNameVisible(true);
        as.customName(net.coremc.foundation.util.ColorUtil.parse(holoText(def, count, variant)));
        as.getPersistentDataContainer().set(HOLO_KEY, PersistentDataType.STRING, "1");
        holograms.put(key(blockLoc), as);
    }

    private void updateHologram(final Location blockLoc, final SpawnerDef def, final int count, final String variant) {
        final String k = key(blockLoc);
        ArmorStand as = holograms.get(k);
        if (as == null || !as.isValid()) {
            spawnHologram(blockLoc, def, count, variant);
            return;
        }
        as.customName(net.coremc.foundation.util.ColorUtil.parse(holoText(def, count, variant)));
    }

    public void spawnHologram(final Location blockLoc, final SpawnerDef def, final int count) {
        spawnHologram(blockLoc, def, count, "normal");
    }

    public void updateHologram(final Location blockLoc, final SpawnerDef def, final int count) {
        updateHologram(blockLoc, def, count, "normal");
    }

    private void removeHologram(final Location blockLoc) {
        final String k = key(blockLoc);
        final ArmorStand as = holograms.remove(k);
        if (as != null && as.isValid()) {
            as.remove();
        }
    }

    /** Remove all holograms (called on plugin disable). */
    public void shutdown() {
        for (final ArmorStand as : holograms.values()) {
            if (as.isValid()) as.remove();
        }
        holograms.clear();
    }

    private void startTicker() {
        // One task per second; each placed spawner acts when its interval has elapsed.
        // Stacked mobs are created with setAI(false) (see applyStackAppearance), so they
        // are already inert display bodies — no per-tick target clearing is required.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            final long now = System.currentTimeMillis() / 1000L;
            for (final PlacedSpawner s : placed.values()) {
                final SpawnerDef def = get(s.spawnerId());
                if (def == null) {
                    continue;
                }
                if (now % def.interval() != 0) {
                    continue;
                }
                final Location loc = locFromKey(s.key());
                if (loc == null || loc.getWorld() == null) {
                    continue;
                }
                // A pile of N spawners spawns N mobs per interval (a 1024x pile = 1024x rate).
                final int pile = s.count();
                final Location spot = loc.clone().add(0.5, 1, 0.5);
                for (int i = 0; i < pile; i++) {
                    spawnOrStack(spot, def, s.islandId(), s.spawnerId(), s.variant());
                }
            }
        }, 20L, 20L);
    }

    /** Max mobs in a single stack pile (across all spawners of the same mob). */
    public int stackMax() {
        return Math.max(1, plugin.getConfig().getInt("spawners.stack-max", 2000));
    }

    /**
     * Spawn a mob from a spawner, or add to the existing stack pile of the SAME MOB
     * near the spawn point — regardless of which spawner it came from. Multiple
     * spawners of the same mob merge into one pile. Stacked mobs have no AI, stay
     * still, never despawn, and show a floating "<Mob> Nx" name tag.
     *
     * @return the (possibly newly created) stacked entity, or null if the pile is
     *         already at the cap and cannot accept another mob this tick.
     */
    public org.bukkit.entity.LivingEntity spawnOrStack(final Location loc, final SpawnerDef def,
                                                       final int islandId, final String spawnerId,
                                                       final String variantId) {
        final SpawnerVariant variant = def.variant(variantId);
        final EntityType mobType = variant.effectiveMob(def.mob());
        // Find an existing stack pile of the SAME mob nearby (any contributing spawner).
        org.bukkit.entity.LivingEntity existing = null;
        for (final org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, 2.0, 2.0, 2.0)) {
            if (!(e instanceof org.bukkit.entity.LivingEntity le)) {
                continue;
            }
            if (le.getType() != mobType) {
                continue;
            }
            if (!le.getPersistentDataContainer().has(STACK_KEY, PersistentDataType.INTEGER)) {
                continue;
            }
            existing = le;
            break;
        }
        if (existing != null) {
            final int count = existing.getPersistentDataContainer().get(STACK_KEY, PersistentDataType.INTEGER);
            if (count >= stackMax()) {
                return existing; // pile full; wait for the next kill to free a slot
            }
            existing.getPersistentDataContainer().set(STACK_KEY, PersistentDataType.INTEGER, count + 1);
            // Record this spawner as a contributor so kills still credit its progression.
            addContributor(existing, islandId, spawnerId, variantId);
            applyStackAppearance(existing, mobType, count + 1);
            return existing;
        }
        // No pile yet — spawn a fresh one.
        final var mobClass = mobType.getEntityClass();
        if (mobClass == null || !org.bukkit.entity.LivingEntity.class.isAssignableFrom(mobClass)) {
            return null;
        }
        final var ent = loc.getWorld().spawn(loc,
                mobClass.asSubclass(org.bukkit.entity.LivingEntity.class));
        final org.bukkit.entity.LivingEntity le = ent;
        net.coremc.skyblock.spawners.listener.SpawnerListener.tagSpawn(le, islandId, spawnerId, variantId);
        le.getPersistentDataContainer().set(STACK_KEY, PersistentDataType.INTEGER, 1);
        applyStackAppearance(le, mobType, 1);
        return le;
    }

    /** @deprecated use {@link #spawnOrStack(Location, SpawnerDef, int, String, String)}. */
    public org.bukkit.entity.LivingEntity spawnOrStack(final Location loc, final SpawnerDef def,
                                                       final int islandId, final String spawnerId) {
        return spawnOrStack(loc, def, islandId, spawnerId, "normal");
    }

    /** Record a spawner as a contributor (islandId:spawnerId:variant). */
    private void addContributor(final org.bukkit.entity.LivingEntity le, final int islandId,
                                final String spawnerId, final String variantId) {
        final String src = le.getPersistentDataContainer().get(SPAWN_TAG_KEY, PersistentDataType.STRING);
        final String add = islandId + ":" + spawnerId + ":" + variantId;
        if (src == null || src.isEmpty()) {
            le.getPersistentDataContainer().set(SPAWN_TAG_KEY, PersistentDataType.STRING, add);
        } else if (!src.contains(add)) {
            le.getPersistentDataContainer().set(SPAWN_TAG_KEY, PersistentDataType.STRING, src + "," + add);
        }
    }

    /**
     * Apply the "stacked / passive" appearance and floating stack label.
     *
     * <p>A stacked mob is a pure physical display body: it has <b>no AI</b> (so it never
     * wandles, targets players, or animates aggressively) but it still obeys gravity and
     * collision, so players and water can push it. {@code setAI(false)} does NOT disable
     * gravity/collision on modern Paper — those are controlled separately — so we keep
     * gravity and collision ON deliberately.</p>
     */
    public void applyStackAppearance(final org.bukkit.entity.LivingEntity le, final EntityType mob, final int count) {
        // No AI: mob never wanders, targets, or path-finds. Gravity + collision remain ON.
        if (le instanceof org.bukkit.entity.Mob m) {
            m.setAI(false);
        }
        le.setSilent(true);
        le.setRemoveWhenFarAway(false);
        le.setPersistent(true);
        le.setCanPickupItems(false);
        // Collidable TRUE so players/water can push the stacked mob (physics).
        le.setCollidable(true);
        le.setGravity(true);
        le.setCustomNameVisible(true);
        final net.kyori.adventure.text.Component name = net.kyori.adventure.text.Component.text(
                        prettyMob(mob) + " " + count + "x",
                        net.kyori.adventure.text.format.TextColor.color(0xFFD700))
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD,
                        net.kyori.adventure.text.format.TextDecoration.State.TRUE)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                        net.kyori.adventure.text.format.TextDecoration.State.FALSE);
        le.customName(name);
    }

    /** Namespaced key for the spawner source tag (shared with SpawnerListener). */
    public static final org.bukkit.NamespacedKey SPAWN_TAG_KEY = new org.bukkit.NamespacedKey("coremc", "cmc_spawn_src");
    /** Namespaced key for the current stack size on a spawned mob. */
    public static final org.bukkit.NamespacedKey STACK_KEY = new org.bukkit.NamespacedKey("coremc", "cmc_stack");
    public static final org.bukkit.NamespacedKey SPAWN_TIME_KEY = new org.bukkit.NamespacedKey("coremc", "cmc_spawn_time");

    public static String prettyMob(final EntityType t) {
        final String n = t.name().toLowerCase().replace("_", " ");
        return n.charAt(0) + n.substring(1);
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

    private static String key(final Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }
}
