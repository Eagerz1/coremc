package net.coremc.skyblock.omnitools.ability;

import net.coremc.skyblock.omnitools.stat.ModifierEngine;
import net.coremc.skyblock.omnitools.stat.Stat;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An OmniTool ACTIVE ability (unlocked by Tool Level).
 *
 * <p>Abilities are config-driven under {@code omnitools.abilities.<ROLE>.<id>}: they declare
 * an {@code unlock-level}, {@code cooldown-seconds}, optional {@code duration-seconds},
 * a behaviour {@code effect} id, and effect-specific config (e.g. {@code radius}, {@code stats},
 * {@code heal}). The {@link AbilityManager} owns runtime state (cooldowns, active durations)
 * and exposes any active buff through the central modifier engine.</p>
 *
 * <p>Effect kinds (handled centrally in AbilityManager):</p>
 * <ul>
 *   <li>{@code buff}         — temporarily adds the listed {@code stats} to the modifier snapshot.</li>
 *   <li>{@code area_mine}    — on activation, mines a cube of ore around the player (radius).</li>
 *   <li>{@code area_harvest} — on activation, harvests a cube of crops/logs around the player.</li>
 * </ul>
 */
public final class OmniAbility {

    public static final class Registry {
        private final Map<String, List<OmniAbility>> byRole = new LinkedHashMap<>();

        public static Registry load(final org.bukkit.configuration.file.FileConfiguration cfg) {
            final Registry r = new Registry();
            final ConfigurationSection root = cfg.getConfigurationSection("omnitools.abilities");
            if (root == null) return r;
            for (final String role : root.getKeys(false)) {
                final ConfigurationSection roleSec = root.getConfigurationSection(role);
                if (roleSec == null) continue;
                final List<OmniAbility> list = new ArrayList<>();
                for (final String id : roleSec.getKeys(false)) {
                    final ConfigurationSection s = roleSec.getConfigurationSection(id);
                    if (s == null) continue;
                    list.add(new OmniAbility(
                            id,
                            role.toUpperCase(Locale.ROOT),
                            s.getString("name", id),
                            s.getString("description", ""),
                            s.getInt("unlock-level", 1),
                            s.getLong("cooldown-seconds", 60),
                            s.getLong("duration-seconds", 0),
                            s.getString("effect", "buff"),
                            s.getInt("radius", 1),
                            s.getDouble("heal", 0),
                            s.getConfigurationSection("stats")));
                }
                list.sort((a, b) -> Integer.compare(a.unlockLevel, b.unlockLevel));
                r.byRole.put(role.toUpperCase(Locale.ROOT), list);
            }
            return r;
        }

        public List<OmniAbility> forRole(final String role) {
            return byRole.getOrDefault(role.toUpperCase(Locale.ROOT), List.of());
        }

        public OmniAbility byId(final String role, final String id) {
            for (final OmniAbility a : forRole(role)) {
                if (a.id.equalsIgnoreCase(id)) return a;
            }
            return null;
        }
    }

    public final String id;
    public final String role;
    public final String name;
    public final String description;
    public final int unlockLevel;
    public final long cooldownSeconds;
    public final long durationSeconds;
    public final String effect;     // behaviour id (buff / area_mine / area_harvest)
    public final int radius;
    public final double heal;
    private final ConfigurationSection stats;

    public OmniAbility(final String id, final String role, final String name, final String description,
                       final int unlockLevel, final long cooldownSeconds, final long durationSeconds,
                       final String effect, final int radius, final double heal,
                       final ConfigurationSection stats) {
        this.id = id;
        this.role = role;
        this.name = name;
        this.description = description;
        this.unlockLevel = unlockLevel;
        this.cooldownSeconds = cooldownSeconds;
        this.durationSeconds = durationSeconds;
        this.effect = effect == null ? "buff" : effect.toLowerCase(Locale.ROOT);
        this.radius = Math.max(0, radius);
        this.heal = heal;
        this.stats = stats;
    }

    public boolean unlockedAt(final int toolLevel) {
        return toolLevel >= unlockLevel;
    }

    /** Stats this ability grants while active (percent). Empty if none. */
    public List<ModifierEngine.Modifier> modifiers() {
        final List<ModifierEngine.Modifier> out = new ArrayList<>();
        if (stats == null) return out;
        for (final String key : stats.getKeys(false)) {
            final Stat st = Stat.fromConfig(key);
            if (st == null) continue;
            out.add(ModifierEngine.Modifier.percent(st, stats.getDouble(key, 0.0)));
        }
        return out;
    }
}
