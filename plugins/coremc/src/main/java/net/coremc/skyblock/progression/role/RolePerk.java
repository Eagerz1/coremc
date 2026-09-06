package net.coremc.skyblock.progression.role;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Role perk framework.
 *
 * <p>Perks are defined entirely in config under {@code role-perks.<role>} as a list of
 * entries. Each perk has a unique id, a role requirement, an unlock level, a description,
 * an {@code effect} type and a {@code values} map. Adding a new perk never requires a code
 * change here — the effect type is interpreted centrally by {@link RoleModifiers}, so new
 * effect kinds are added by handling the id in that one service.</p>
 *
 * <p>Perks are PASSIVE and continuous (e.g. "+10% mining XP", "+5% Core contribution from
 * mining"). One-time milestone grants (tokens, cosmetics, Omnitool unlocks) live in
 * {@link RoleMilestone} instead. This separation keeps the two concepts cleanly
 * extensible and avoids putting every perk into one giant listener.</p>
 *
 * <p>Known effect types (interpreted by {@link RoleModifiers}):</p>
 * <ul>
 *   <li>{@code xp_multiplier}        — bonus role XP from this role's activity (values.multiplier)</li>
 *   <li>{@code token_multiplier}     — bonus Sky Tokens from this role's activity (values.multiplier)</li>
 *   <li>{@code core_money_multiplier}— bonus Core money from this role's activity (values.multiplier)</li>
 *   <li>{@code core_token_multiplier}— bonus Core Sky Tokens from this role's activity (values.multiplier)</li>
 *   <li>{@code drop_chance}          — bonus drop chance (values.chance, 0..1)</li>
 *   <li>{@code rare_drop_chance}     — bonus rare drop chance (values.chance, 0..1)</li>
 *   <li>{@code replant_chance}       — chance to auto-replant (farming) (values.chance)</li>
 *   <li>{@code feller_chance}        — chance to fell whole tree (logging) (values.chance)</li>
 *   <li>{@code economy_multiplier}   — bonus sell value (values.multiplier)</li>
 *   <li>{@code omnitool_xp_multiplier}— bonus Omnitool tool XP (values.multiplier)</li>
 *   <li>{@code ability}              — unlocks a named ability (values.ability); exposed as a flag</li>
 * </ul>
 */
public final class RolePerk {

    /** All perk definitions, grouped by role (lower-case role id). */
    public static final class Registry {
        private final Map<String, List<RolePerk>> byRole = new LinkedHashMap<>();

        public static Registry load(final JavaPlugin plugin) {
            final Registry r = new Registry();
            final ConfigurationSection root = plugin.getConfig().getConfigurationSection("role-perks");
            if (root == null) return r;
            for (final String role : root.getKeys(false)) {
                final ConfigurationSection roleSec = root.getConfigurationSection(role);
                if (roleSec == null) continue;
                final List<RolePerk> list = new ArrayList<>();
                for (final String id : roleSec.getKeys(false)) {
                    final ConfigurationSection s = roleSec.getConfigurationSection(id);
                    if (s == null) continue;
                    list.add(new RolePerk(
                            id,
                            role.toUpperCase(java.util.Locale.ROOT),
                            s.getInt("unlock-level", 1),
                            s.getString("name", id),
                            s.getString("description", ""),
                            s.getString("effect", ""),
                            s.getConfigurationSection("values"),
                            s.getBoolean("hidden", false)));
                }
                list.sort((a, b) -> Integer.compare(a.unlockLevel, b.unlockLevel));
                r.byRole.put(role.toLowerCase(java.util.Locale.ROOT), list);
            }
            return r;
        }

        public List<RolePerk> forRole(final String role) {
            return byRole.getOrDefault(role.toLowerCase(java.util.Locale.ROOT), List.of());
        }

        public RolePerk byId(final String role, final String id) {
            for (final RolePerk p : forRole(role)) {
                if (p.id.equalsIgnoreCase(id)) return p;
            }
            return null;
        }
    }

    public final String id;
    public final String role;       // upper-case role id (matches RoleManager.Role.name())
    public final int unlockLevel;
    public final String name;
    public final String description;
    public final String effect;
    public final ConfigurationSection values;
    public final boolean hidden;

    public RolePerk(final String id, final String role, final int unlockLevel, final String name,
                    final String description, final String effect, final ConfigurationSection values,
                    final boolean hidden) {
        this.id = id;
        this.role = role;
        this.unlockLevel = unlockLevel;
        this.name = name;
        this.description = description;
        this.effect = effect == null ? "" : effect.toLowerCase(java.util.Locale.ROOT);
        this.values = values;
        this.hidden = hidden;
    }

    /** Whether this perk is unlocked at the given role level. */
    public boolean unlockedAt(final int level) {
        return level >= unlockLevel;
    }

    public double valueDouble(final String key, final double def) {
        return values == null ? def : values.getDouble(key, def);
    }

    public String valueString(final String key, final String def) {
        return values == null ? def : values.getString(key, def);
    }
}
