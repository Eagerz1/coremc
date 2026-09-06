package net.coremc.skyblock.crates;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Physical crate blocks for every CoreMC key.
 *
 * <p>Each key (config {@code crates.keys.<id>.crate}) can have a placeable crate block.
 * The block is the configured {@code material} (e.g. a barrel / trapdoor) tagged with
 * PDC {@code coremc.crate = <keyid>}. A floating, invisible armour stand above the block
 * shows the configured {@code floating-name}. Right-clicking the block opens the key
 * (consuming one matching key from the player's inventory — existing key integration).
 * An optional fixed {@code location} ("world,x,y,z") can be set so a crate is always
 * present at a spawn / hub spot; those crates are recreated on plugin enable.</p>
 */
public final class CrateBlock implements Listener {

    private static final String PDC = "coremc.crate";
    private final JavaPlugin plugin;
    private final KeyItem keyItem;
    private final CrateModule module;
    // In-memory map of placed crate blocks (location string -> key id).
    private final java.util.Map<String, KeyId> placed = new java.util.concurrent.ConcurrentHashMap<>();

    public CrateBlock(final JavaPlugin plugin, final KeyItem keyItem, final CrateModule module) {
        this.plugin = plugin;
        this.keyItem = keyItem;
        this.module = module;
    }

    /** Build a physical crate-block item for a key (placeable, opens the key on right-click). */
    public ItemStack build(final KeyId id, final int amount) {
        final var cfg = plugin.getConfig();
        final String section = "crates.keys." + id.id() + ".crate";
        final String matName = cfg.getString(section + ".material", "BARREL");
        Material material;
        try {
            material = Material.valueOf(matName.toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            material = Material.BARREL;
        }
        final String keyColour = cfg.getString("crates.keys." + id.id() + ".colour", "white");
        final String name = cfg.getString("crates.keys." + id.id() + ".name", id.defaultName());
        final ItemStack it = new ItemStack(material, Math.max(1, Math.min(amount, 64)));
        final ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<bold><" + keyColour + ">" + name.replace(" Key", " Crate")
                            + "</" + keyColour + "></bold>")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                            net.kyori.adventure.text.format.TextDecoration.State.FALSE));
            final List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            final var mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
            lore.add(mm.deserialize("<white>Right-click to open a " + name + ".</white>"));
            lore.add(mm.deserialize("<gray>Consumes one key from your inventory.</gray>"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, PDC), PersistentDataType.STRING, id.id());
            it.setItemMeta(meta);
        }
        return it;
    }

    /** True if the item is a CoreMC crate block. */
    public boolean isCrate(final ItemStack item) {
        return crateId(item) != null;
    }

    /** Return the key id a crate block represents, or null. */
    public KeyId crateId(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        final String tag = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, PDC), PersistentDataType.STRING);
        return tag == null ? null : KeyId.byId(tag);
    }

    /** Recreate any crate configured with a fixed {@code location} (hub / spawn crates). */
    public void spawnConfiguredLocations() {
        for (final KeyId id : KeyId.values()) {
            final String locStr = plugin.getConfig()
                    .getString("crates.keys." + id.id() + ".crate.location", "");
            if (locStr == null || locStr.isBlank()) continue;
            final Location loc = parseLocation(locStr);
            if (loc == null) {
                plugin.getLogger().warning("[crates] bad crate location for " + id.id() + ": " + locStr);
                continue;
            }
            placeCrate(loc, id);
        }
    }

    private static String key(final Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location parseLocation(final String s) {
        // world,x,y,z
        final String[] parts = s.split(",");
        if (parts.length < 4) return null;
        final org.bukkit.World w = Bukkit.getWorld(parts[0].trim());
        if (w == null) return null;
        try {
            final double x = Double.parseDouble(parts[1].trim());
            final double y = Double.parseDouble(parts[2].trim());
            final double z = Double.parseDouble(parts[3].trim());
            return new Location(w, x, y, z);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    /** Place a crate block at the location and attach a floating name. */
    private void placeCrate(final Location loc, final KeyId id) {
        final Block block = loc.getBlock();
        final Material mat = build(id, 1).getType();
        block.setType(mat, false);
        placed.put(key(loc), id);
        attachName(block, id);
    }

    /** Spawn the floating crate-name armour stand above a placed block. */
    private void attachName(final Block block, final KeyId id) {
        final String raw = plugin.getConfig()
                .getString("crates.keys." + id.id() + ".crate.floating-name",
                        "<bold>" + id.defaultName().replace(" Key", " Crate") + "</bold>");
        final String legacy = miniToLegacy(raw);
        final Location standLoc = block.getLocation().clone().add(0.5, 1.2, 0.5);
        final ArmorStand stand = (ArmorStand) block.getWorld().spawnEntity(standLoc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setMarker(true);
        stand.setCustomName(legacy);
        stand.setCustomNameVisible(true);
        stand.getPersistentDataContainer().set(
                new NamespacedKey(plugin, PDC + ".name"), PersistentDataType.STRING, id.id());
    }

    /** Minimal MiniMessage -> legacy (&/section) converter supporting the tags used in
     *  crate floating-name configs: <bold>, <italic>, <colour>, plus closing tags. */
    private static String miniToLegacy(final String mini) {
        final StringBuilder sb = new StringBuilder();
        int i = 0;
        final int n = mini.length();
        while (i < n) {
            final char c = mini.charAt(i);
            if (c == '<') {
                final int end = mini.indexOf('>', i);
                if (end < 0) { sb.append(c); i++; continue; }
                final String tag = mini.substring(i + 1, end).trim().toLowerCase(java.util.Locale.ROOT);
                if (tag.startsWith("/")) {
                    // closing tag: reset formatting to keep it simple (close bold/colour).
                    sb.append('§').append('r');
                } else if (tag.equals("bold")) {
                    sb.append('§').append('l');
                } else if (tag.equals("italic")) {
                    sb.append('§').append('o');
                } else if (tag.equals("underline")) {
                    sb.append('§').append('n');
                } else if (tag.equals("strike")) {
                    sb.append('§').append('m');
                } else {
                    final String code = LEGACY_COLOURS.get(tag);
                    if (code != null) sb.append('§').append(code);
                }
                i = end + 1;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static final java.util.Map<String, String> LEGACY_COLOURS = java.util.Map.ofEntries(
            java.util.Map.entry("black", "0"), java.util.Map.entry("dark_blue", "1"),
            java.util.Map.entry("dark_green", "2"), java.util.Map.entry("dark_aqua", "3"),
            java.util.Map.entry("dark_red", "4"), java.util.Map.entry("dark_purple", "5"),
            java.util.Map.entry("gold", "6"), java.util.Map.entry("gray", "7"),
            java.util.Map.entry("grey", "7"), java.util.Map.entry("dark_gray", "8"),
            java.util.Map.entry("dark_grey", "8"), java.util.Map.entry("blue", "9"),
            java.util.Map.entry("green", "a"), java.util.Map.entry("aqua", "b"),
            java.util.Map.entry("red", "c"), java.util.Map.entry("light_purple", "d"),
            java.util.Map.entry("pink", "d"), java.util.Map.entry("yellow", "e"),
            java.util.Map.entry("white", "f"));

    @EventHandler
    public void onCratePlace(final BlockPlaceEvent ev) {
        final ItemStack item = ev.getItemInHand();
        final KeyId id = crateId(item);
        if (id == null) return;
        final Block block = ev.getBlock();
        placed.put(key(block.getLocation()), id);
        // Floating name appears on the next tick (after the block state is committed).
        final KeyId fid = id;
        new BukkitRunnable() {
            @Override public void run() { attachName(block, fid); }
        }.runTaskLater(plugin, 1L);
        ev.getPlayer().sendMessage(CoreFoundation.getInstance().messages().parse(
                "<prefix> <gray>Placed a " + fid.defaultName().replace(" Key", " Crate")
                        + ". Right-click it to open a " + fid.defaultName() + ".</gray>",
                plugin.getConfig().getString("prefix")));
    }

    @EventHandler
    public void onCrateInteract(final PlayerInteractEvent ev) {
        if (ev.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        final Block block = ev.getClickedBlock();
        if (block == null) return;
        final KeyId id = placed.get(key(block.getLocation()));
        if (id == null) return;
        ev.setCancelled(true);
        final Player p = ev.getPlayer();
        if (module.consumeKey(p, id)) {
            module.opener().open(p, id);
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        } else {
            p.sendMessage(CoreFoundation.getInstance().messages().parse(
                    "<prefix> <red>You do not have a " + id.defaultName() + ".</red>",
                    plugin.getConfig().getString("prefix")));
        }
    }

    @EventHandler
    public void onCrateBreak(final BlockBreakEvent ev) {
        final Block block = ev.getBlock();
        final String k = key(block.getLocation());
        final KeyId id = placed.remove(k);
        if (id == null) return;
        // Do not drop a vanilla block; drop the crate item instead so it stays usable.
        ev.setDropItems(false);
        final Location loc = block.getLocation();
        final KeyId fid = id;
        // Remove any floating name stand above.
        new BukkitRunnable() {
            @Override public void run() {
                for (final org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(
                        loc.clone().add(0.5, 1.2, 0.5), 0.6, 0.6, 0.6)) {
                    if (e instanceof ArmorStand as && as.getPersistentDataContainer().has(
                            new NamespacedKey(plugin, PDC + ".name"), PersistentDataType.STRING)) {
                        e.remove();
                    }
                }
                final ItemStack drop = build(fid, 1);
                loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), drop);
            }
        }.runTaskLater(plugin, 1L);
    }
}
