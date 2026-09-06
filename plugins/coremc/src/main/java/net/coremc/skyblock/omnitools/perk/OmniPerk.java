package net.coremc.skyblock.omnitools.perk;

import net.coremc.skyblock.omnitools.stat.ModifierEngine;
import net.coremc.skyblock.omnitools.stat.Stat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OmniTool PERK framework (unlocked by <b>Tool Level</b>, separate from the
 * Role-perk system which is unlocked by Role Level).
 *
 * <p>Perks are 100% config-driven under {@code omnitools.perks.<ROLE>.<id>}. Each entry
 * declares an {@code unlock-level}, an optional {@code stats} map (Stat name -&gt; percent
 * bonus) and/or an {@code ability} id. Adding a perk is a config edit only — no code
 * change. Code-driven perks can still implement the {@link Perk} interface and be
 * registered via {@link #registerFallback}.</p>
 *
 * <p>The {@link net.coremc.skyblock.omnitools.tool.ToolManager} reads this registry when
 * building the central modifier snapshot, so every perk bonus flows through the single
 * {@link ModifierEngine} (no bonus is hardcoded inside a listener).</p>
 */
public final class OmniPerk {

    /** All OmniTool perks, grouped by upper-case role id. */
    public static final class Registry {
        private final Map<String, List<OmniPerk>> byRole = new LinkedHashMap<>();

        public static Registry load(final org.bukkit.configuration.file.FileConfiguration cfg) {
            final Registry r = new Registry();
            final ConfigurationSection root = cfg.getConfigurationSection("omnitools.perks");
            if (root == null) return r;
            for (final String role : root.getKeys(false)) {
                final ConfigurationSection roleSec = root.getConfigurationSection(role);
                if (roleSec == null) continue;
                final List<OmniPerk> list = new ArrayList<>();
                for (final String id : roleSec.getKeys(false)) {
                    final ConfigurationSection s = roleSec.getConfigurationSection(id);
                    if (s == null) continue;
                    list.add(new OmniPerk(
                            id,
                            role.toUpperCase(Locale.ROOT),
                            s.getString("name", id),
                            s.getString("description", ""),
                            s.getInt("unlock-level", 1),
                            s.getString("ability", ""),
                            s.getConfigurationSection("stats"),
                            s.getBoolean("hidden", false)));
                }
                list.sort((a, b) -> Integer.compare(a.unlockLevel, b.unlockLevel));
                r.byRole.put(role.toUpperCase(Locale.ROOT), list);
            }
            return r;
        }

        public List<OmniPerk> forRole(final String role) {
            return byRole.getOrDefault(role.toUpperCase(Locale.ROOT), List.of());
        }

        public OmniPerk byId(final String role, final String id) {
            for (final OmniPerk p : forRole(role)) {
                if (p.id.equalsIgnoreCase(id)) return p;
            }
            return null;
        }
    }

    public final String id;
    public final String role;        // upper-case role id
    public final String name;
    public final String description;
    public final int unlockLevel;
    public final String ability;     // ability id, or "" if none
    public final boolean hidden;
    private final ConfigurationSection stats;

    public OmniPerk(final String id, final String role, final String name, final String description,
                    final int unlockLevel, final String ability, final ConfigurationSection stats,
                    final boolean hidden) {
        this.id = id;
        this.role = role;
        this.name = name;
        this.description = description;
        this.unlockLevel = unlockLevel;
        this.ability = ability == null ? "" : ability;
        this.stats = stats;
        this.hidden = hidden;
    }

    /** Whether this perk is unlocked at the given Tool Level. */
    public boolean unlockedAt(final int toolLevel) {
        return toolLevel >= unlockLevel;
    }

    /** Stats this perk contributes, as modifiers (percent). Empty if none. */
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

    /** Compact effect line for lore/GUI, e.g. "+10% Mining Speed". */
    public String effectLine() {
        final StringBuilder sb = new StringBuilder();
        if (stats != null) {
            for (final String key : stats.getKeys(false)) {
                final Stat st = Stat.fromConfig(key);
                if (st == null) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append("+").append((int) Math.round(stats.getDouble(key, 0.0) * 100)).append("% ")
                        .append(st.display());
            }
        }
        if (!ability.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("Ability: ").append(ability);
        }
        return sb.toString();
    }
}
