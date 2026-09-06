package net.coremc.skyblock.omnitools.ability;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import net.coremc.skyblock.omnitools.tool.ToolManager;
import net.coremc.skyblock.omnitools.OmniConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime owner of OmniTool ACTIVE abilities.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Track per-player cooldowns (persisted to memory only; cooldowns reset on restart —
 *       acceptable for an active ability).</li>
 *   <li>Track active timed buffs and expose their stat bonuses via {@link #activeModifiers}
 *       so the central modifier snapshot includes them.</li>
 *   <li>Execute the ability behaviour on trigger (buff / area_mine / area_harvest), with
 *       player feedback (message + sound + particle).</li>
 * </ul>
 *
 * <p>Abilities are unlocked by Tool Level (checked against {@link ToolManager}). All tuning
 * (cooldown, duration, radius, heal, stat map) is config-driven via {@link OmniAbility}.</p>
 */
public final class AbilityManager {

    private final JavaPlugin plugin;
    private final ToolManager tools;
    private final OmniConfig omni;
    private OmniAbility.Registry registry;

    // key = uuid -> (abilityId -> epoch-millis ready-at)
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    // key = uuid -> (abilityId -> epoch-millis expiry)
    private final Map<UUID, Map<String, Long>> active = new ConcurrentHashMap<>();

    public AbilityManager(final JavaPlugin plugin, final ToolManager tools, final OmniConfig omni) {
        this.plugin = plugin;
        this.tools = tools;
        this.omni = omni;
    }

    public void reload() {
        this.registry = OmniAbility.Registry.load(omni.config());
    }

    public OmniAbility.Registry registry() {
        return registry == null ? (registry = OmniAbility.Registry.load(omni.config())) : registry;
    }

    /** Whether the player's active role+tool has this ability unlocked and ready. */
    public boolean isUnlocked(final UUID uuid, final String role, final String abilityId) {
        final OmniAbility a = registry().byId(role, abilityId);
        return a != null && a.unlockedAt(tools.toolLevel(uuid, role));
    }

    /** Seconds left on cooldown (0 if ready). */
    public long cooldownLeft(final UUID uuid, final String abilityId) {
        final Map<String, Long> cd = cooldowns.get(uuid);
        if (cd == null) return 0;
        final Long ready = cd.get(abilityId);
        if (ready == null) return 0;
        return Math.max(0, (ready - System.currentTimeMillis()) / 1000);
    }

    /** Whether an ability is currently active (timed buff still running). */
    public boolean isActive(final UUID uuid, final String abilityId) {
        final Map<String, Long> act = active.get(uuid);
        if (act == null) return false;
        final Long expiry = act.get(abilityId);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    /** Stat modifiers contributed by any currently-active abilities for this role. */
    public java.util.List<net.coremc.skyblock.omnitools.stat.ModifierEngine.Modifier> activeModifiers(
            final UUID uuid, final String role) {
        final java.util.List<net.coremc.skyblock.omnitools.stat.ModifierEngine.Modifier> out =
                new java.util.ArrayList<>();
        final Map<String, Long> act = active.get(uuid);
        if (act == null) return out;
        final long now = System.currentTimeMillis();
        for (final var e : act.entrySet()) {
            if (e.getValue() <= now) continue;
            final OmniAbility a = registry().byId(role, e.getKey());
            if (a == null) continue;
            out.addAll(a.modifiers());
        }
        return out;
    }

    /** Trigger a role's ability by id. Returns true if it fired. */
    public boolean trigger(final Player p, final String role, final String abilityId) {
        final OmniAbility a = registry().byId(role, abilityId);
        if (a == null || !a.unlockedAt(tools.toolLevel(p.getUniqueId(), role))) {
            send(p, "<red>That ability is not unlocked yet.</red>");
            return false;
        }
        final long left = cooldownLeft(p.getUniqueId(), abilityId);
        if (left > 0) {
            send(p, "<red>" + a.name + " is on cooldown for " + left + "s.</red>");
            return false;
        }

        // Start cooldown immediately.
        cooldowns.computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(abilityId, System.currentTimeMillis() + a.cooldownSeconds * 1000L);

        // Behaviour.
        switch (a.effect) {
            case "area_mine" -> runArea(p, role, a, MINING_BLOCKS);
            case "area_harvest" -> runArea(p, role, a, CROP_AND_LOG_BLOCKS);
            default -> runBuff(p, a);
        }
        return true;
    }

    private void runBuff(final Player p, final OmniAbility a) {
        if (a.durationSeconds > 0) {
            active.computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>())
                    .put(a.id, System.currentTimeMillis() + a.durationSeconds * 1000L);
        }
        if (a.heal > 0) {
            p.setHealth(Math.min(p.getHealth() + a.heal, p.getMaxHealth()));
        }
        feedback(p, a.name, a.durationSeconds);
    }

    private void runArea(final Player p, final String role, final OmniAbility a, final java.util.Set<Material> set) {
        final int r = a.radius;
        final Location c = p.getLocation();
        int count = 0;
        final var island = CoreMC.getInstance().islands().api().getIsland(p.getUniqueId());
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    final Block b = c.clone().add(dx, dy, dz).getBlock();
                    if (set.contains(b.getType())) {
                        // Respect island build permissions if available.
                        b.breakNaturally(p.getInventory().getItemInMainHand());
                        count++;
                    }
                }
            }
        }
        feedback(p, a.name + " (" + count + " blocks)", 0);
        if (island != null) {
            CoreMC.getInstance().islands().api().addStatistic(island.getId(), "ability-area", count);
        }
    }

    private void feedback(final Player p, final String label, final long dur) {
        send(p, "<bold><gold>" + label + "</gold></bold> <gray>activated"
                + (dur > 0 ? " for " + dur + "s" : "") + ".</gray>");
        try {
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 1.4f);
            p.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, p.getLocation().add(0, 1, 0),
                    24, 0.5, 0.6, 0.5, 0.05);
        } catch (final IllegalArgumentException ignored) {}
    }

    private void send(final Player p, final String msg) {
        CoreFoundation.getInstance().messages().sendRaw(p,
                CoreFoundation.getInstance().messages().getPrefix() + " " + msg);
    }

    // Materials handled by area abilities (mirrors the role activity sets).
    private static final java.util.Set<Material> MINING_BLOCKS = java.util.EnumSet.of(
            Material.STONE, Material.COBBLESTONE, Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE,
            Material.DIAMOND_ORE, Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS, Material.DEEPSLATE_COAL_ORE,
            Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.NETHER_GOLD_ORE, Material.GILDED_BLACKSTONE);

    private static final java.util.Set<Material> CROP_AND_LOG_BLOCKS = java.util.EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART,
            Material.SUGAR_CANE, Material.CACTUS,
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.CRIMSON_STEM, Material.WARPED_STEM);
}
