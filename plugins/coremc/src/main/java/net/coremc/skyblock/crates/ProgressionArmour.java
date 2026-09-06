package net.coremc.skyblock.crates;

import net.coremc.coremc.CoreMC;
import net.coremc.foundation.CoreFoundation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Virtual CoreMC progression armour sets.
 *
 * <p>Unlike the physical {@link ArmourSets} (real leather armour granted to the
 * inventory), these 8 sets are VIRTUAL: a player OWNS a set and may EQUIP exactly
 * one at a time. They are never physical items, so they cannot be dropped, moved
 * around the island, placed in chests, traded, or lost. Ownership and the
 * currently-equipped set are persisted in {@code progression-armour.yml}.</p>
 *
 * <p>Every set effect requires the FULL set to be "equipped" (these are all-or-nothing
 * virtual sets, so the full-set requirement is satisfied by simply having the set
 * equipped). Effect multipliers are queried by gameplay listeners through the
 * {@code getX...} helpers; only the equipped set contributes.</p>
 */
public final class ProgressionArmour {

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final java.util.Set<String> owned = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, String> equipped = new ConcurrentHashMap<>();

    public ProgressionArmour(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "progression-armour.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection ownedSec = data.getConfigurationSection("owned");
        if (ownedSec != null) {
            for (final String k : ownedSec.getKeys(false)) {
                if (data.getBoolean("owned." + k, false)) owned.add(k);
            }
        }
        final ConfigurationSection eqSec = data.getConfigurationSection("equipped");
        if (eqSec != null) {
            for (final String k : eqSec.getKeys(false)) {
                try {
                    final UUID uuid = UUID.fromString(k);
                    final String setId = data.getString("equipped." + k);
                    if (setId != null && owned.contains(setId)) {
                        equipped.put(uuid, setId);
                    }
                } catch (final IllegalArgumentException ignored) {}
            }
        }
    }

    /** All configured set ids in config order. */
    public List<String> setIds() {
        final ConfigurationSection sec = plugin.getConfig().getConfigurationSection("progression-armour.sets");
        return sec == null ? new ArrayList<>() : new ArrayList<>(sec.getKeys(false));
    }

    public String displayName(final String setId) {
        return plugin.getConfig().getString("progression-armour.sets." + setId + ".name", setId);
    }

    public String type(final String setId) {
        return plugin.getConfig().getString("progression-armour.sets." + setId + ".type", "");
    }

    public String colour(final String setId) {
        return plugin.getConfig().getString("progression-armour.sets." + setId + ".colour", "white");
    }

    public String dyeMaterial(final String setId) {
        return plugin.getConfig().getString("progression-armour.sets." + setId + ".dye", "WHITE_DYE");
    }

    public String obtain(final String setId) {
        return plugin.getConfig().getString("progression-armour.sets." + setId + ".obtain", "Lootbox Legendary Reward");
    }

    public List<String> abilities(final String setId) {
        return plugin.getConfig().getStringList("progression-armour.sets." + setId + ".abilities");
    }

    // ---- ownership ----

    public boolean owns(final UUID uuid, final String setId) {
        return owned.contains(setId);
    }

    public boolean owns(final Player p, final String setId) {
        return owns(p.getUniqueId(), setId);
    }

    /** Grant the full virtual set to a player (idempotent). */
    public void grant(final Player p, final String setId) {
        if (!setIds().contains(setId)) {
            plugin.getLogger().warning("[progression-armour] unknown set: " + setId);
            return;
        }
        if (owned.add(setId)) {
            data.set("owned." + setId, true);
            save();
        }
        p.sendMessage(CoreFoundation.getInstance().messages().parse(
                "<prefix> <" + colour(setId) + ">You unlocked the " + displayName(setId) + " Set!</" + colour(setId) + ">",
                plugin.getConfig().getString("prefix")));
    }

    // ---- equip / unequip ----

    public String equipped(final UUID uuid) {
        return equipped.get(uuid);
    }

    public String equipped(final Player p) {
        return equipped(p.getUniqueId());
    }

    /** Equip a set the player owns. Returns true on success. Only one set equipped at a time. */
    public boolean equip(final Player p, final String setId) {
        if (!owns(p, setId)) return false;
        equipped.put(p.getUniqueId(), setId);
        data.set("equipped." + p.getUniqueId().toString(), setId);
        save();
        applyPassiveEffects(p);
        return true;
    }

    /** Unequip the currently equipped set (if it matches). Returns true if something was unequipped. */
    public boolean unequip(final Player p) {
        final String cur = equipped.remove(p.getUniqueId());
        if (cur == null) return false;
        data.set("equipped." + p.getUniqueId().toString(), null);
        save();
        clearPassiveEffects(p);
        return true;
    }

    /** Toggle equip state: equip if a different/empty set, unequip if already equipped. */
    public void toggle(final Player p, final String setId) {
        final String cur = equipped(p);
        if (cur != null && cur.equals(setId)) {
            unequip(p);
            p.sendMessage(CoreFoundation.getInstance().messages().parse(
                    "<prefix> <gray>You unequipped the " + displayName(setId) + " Set.</gray>",
                    plugin.getConfig().getString("prefix")));
        } else if (equip(p, setId)) {
            p.sendMessage(CoreFoundation.getInstance().messages().parse(
                    "<prefix> <" + colour(setId) + ">You equipped the " + displayName(setId) + " Set!</" + colour(setId) + ">",
                    plugin.getConfig().getString("prefix")));
        } else {
            p.sendMessage(CoreFoundation.getInstance().messages().parse(
                    "<prefix> <red>You do not own the " + displayName(setId) + " Set.</red>",
                    plugin.getConfig().getString("prefix")));
        }
    }

    // ---- effect queries (only the equipped set contributes) ----

    private boolean active(final UUID uuid, final String setId) {
        return setId.equals(equipped.get(uuid));
    }

    /** Drop multiplier for activity-type sets (Tidal/Fishing, Wildwood/Logging, Deepcore/Mining, Harvest/Farming, Bloodfang/Slaying). */
    public double dropMultiplier(final Player p, final String setType) {
        final String eq = equipped(p);
        if (eq == null) return 1.0;
        if (!type(eq).equalsIgnoreCase(setType)) return 1.0;
        // All activity sets grant 1.5x drops.
        if (setType.equalsIgnoreCase("Fishing") || setType.equalsIgnoreCase("Logging")
                || setType.equalsIgnoreCase("Mining") || setType.equalsIgnoreCase("Farming")
                || setType.equalsIgnoreCase("Slaying")) {
            return 1.5;
        }
        return 1.0;
    }

    /** Fortune set: 1.1x more drops (universal). */
    public double fortuneDropMultiplier(final Player p) {
        return active(p.getUniqueId(), "fortune") ? 1.1 : 1.0;
    }

    /** Fortune set: 1.1x sell multiplier (universal). */
    public double fortuneSellMultiplier(final Player p) {
        return active(p.getUniqueId(), "fortune") ? 1.1 : 1.0;
    }

    /** Prosperity set: 1.1x more island tokens (universal). */
    public double prosperityTokenMultiplier(final Player p) {
        return active(p.getUniqueId(), "prosperity") ? 1.1 : 1.0;
    }

    /** Prosperity set: generators run 1.2x faster. */
    public double prosperityGeneratorMultiplier(final Player p) {
        return active(p.getUniqueId(), "prosperity") ? 1.2 : 1.0;
    }

    /** Titan set: 1.5x damage against bosses. */
    public double titanBossDamageMultiplier(final Player p) {
        return active(p.getUniqueId(), "titan") ? 1.5 : 1.0;
    }

    /** Bloodfang set: every mob killed counts as 3 kills while Multi-Kill is active. */
    public boolean bloodfangMultiKill(final Player p) {
        return active(p.getUniqueId(), "bloodfang") && CoreMC_crateModule().bloodfangActive(p);
    }

    /** True if the equipped set grants permanent Speed II. */
    public boolean hasPermanentSpeed(final Player p) {
        return active(p.getUniqueId(), "titan");
    }

    /** True if the equipped set grants permanent Regeneration II. */
    public boolean hasPermanentRegen(final Player p) {
        return active(p.getUniqueId(), "titan");
    }

    private void applyPassiveEffects(final Player p) {
        final String eq = equipped(p);
        if (!"titan".equals(eq)) return;
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SPEED, Integer.MAX_VALUE, 1, true, false));
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.REGENERATION, Integer.MAX_VALUE, 1, true, false));
    }

    private void clearPassiveEffects(final Player p) {
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION);
    }

    private CrateModule CoreMC_crateModule() {
        return CoreMC.getInstance().crates();
    }

    public void save() {
        try {
            data.save(file);
        } catch (final java.io.IOException e) {
            plugin.getLogger().warning("Could not save progression-armour.yml: " + e.getMessage());
        }
    }
}
