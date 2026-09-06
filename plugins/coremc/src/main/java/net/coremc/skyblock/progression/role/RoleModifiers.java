package net.coremc.skyblock.progression.role;

import net.coremc.coremc.CoreMC;
import net.coremc.skyblock.progression.ProgressionModule;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/**
 * Central query service for role-driven multipliers and ability flags.
 *
 * <p>Every gameplay listener that wants to answer "how much does this player's role
 * boost <i>X</i>?" goes through here instead of reading perks directly. That keeps
 * the Role perk logic in one place and, crucially, keeps it <b>additive</b> with the
 * existing modifier architecture:</p>
 * <ul>
 *   <li>Island buffs ({@code ProgressionModule#getBuffMultiplier}) — island-wide
 *       upgrades such as {@code core_money}.</li>
 *   <li>Live events ({@code Events.*}, {@code CoreEventApi}) — seasonal multipliers.</li>
 *   <li>Role perks (this class) — per-player, per-role bonuses from progression.</li>
 * </ul>
 *
 * <p>A listener multiplies its base amount by {@code islandBuff * eventMultiplier * roleMultiplier}.
 * The role component is the only new piece, and it is exposed as a multiplier so it composes
 * cleanly with the others. No global economy is ever touched directly by this class — callers
 * use the existing {@code Economy} / Core systems.</p>
 *
 * <p>Universal is intentionally weaker per-activity: its perk values in config are balanced to
 * be smaller than a specialist's, so it gives a little of everything rather than the full
 * benefit of one role.</p>
 */
public final class RoleModifiers {

    private final ProgressionModule prog;
    private RolePerk.Registry registry;

    public RoleModifiers(final ProgressionModule prog) {
        this.prog = prog;
    }

    /** Reload perk definitions (called on plugin (re)load). */
    public void reload(final org.bukkit.plugin.java.JavaPlugin plugin) {
        this.registry = RolePerk.Registry.load(plugin);
    }

    private RolePerk.Registry registry() {
        return registry == null ? (registry = RolePerk.Registry.load(CoreMC.getInstance())) : registry;
    }

    private RoleManager roles() { return prog.roles(); }

    /** Active role for a player (may be null). */
    public RoleManager.Role activeRole(final UUID uuid) {
        return roles().get(uuid);
    }

    // ---- generic role-perk multiplier (1.0 if none / not applicable) ----

    /** Summed multiplier from all unlocked perks of a given effect for the player's active role. */
    private double roleMultiplier(final UUID uuid, final String effect) {
        final RoleManager.Role role = activeRole(uuid);
        if (role == null) return 1.0;
        double additive = 0.0; // accumulate +X% from each unlocked perk
        for (final RolePerk p : registry().forRole(role.name())) {
            if (!effect.equals(p.effect)) continue;
            if (!p.unlockedAt(roles().getLevel(uuid, role.tree()))) continue;
            additive += p.valueDouble("multiplier", 0.0);
        }
        return 1.0 + additive;
    }

    /** Summed chance (0..1, clamped) from unlocked perks of a given effect. */
    private double roleChance(final UUID uuid, final String effect) {
        final RoleManager.Role role = activeRole(uuid);
        if (role == null) return 0.0;
        double chance = 0.0;
        for (final RolePerk p : registry().forRole(role.name())) {
            if (!effect.equals(p.effect)) continue;
            if (!p.unlockedAt(roles().getLevel(uuid, role.tree()))) continue;
            chance += p.valueDouble("chance", 0.0);
        }
        return Math.max(0.0, Math.min(1.0, chance));
    }

    /** Whether the player's active role has an unlocked perk of effect {@code ability} with the given id. */
    public boolean hasAbility(final UUID uuid, final String abilityId) {
        final RoleManager.Role role = activeRole(uuid);
        if (role == null) return false;
        for (final RolePerk p : registry().forRole(role.name())) {
            if (!"ability".equals(p.effect)) continue;
            if (!p.unlockedAt(roles().getLevel(uuid, role.tree()))) continue;
            if (abilityId.equalsIgnoreCase(p.valueString("ability", ""))) return true;
        }
        return false;
    }

    // ---- public typed queries ----

    /** Bonus role XP multiplier from the player's active role perks (1.0 = none). */
    public double xpMultiplier(final UUID uuid) {
        return roleMultiplier(uuid, "xp_multiplier");
    }

    /** Bonus Sky Token multiplier from the player's active role perks (1.0 = none). */
    public double tokenMultiplier(final UUID uuid) {
        return roleMultiplier(uuid, "token_multiplier");
    }

    /** Bonus Core money multiplier from the player's active role perks (1.0 = none). */
    public double coreMoneyMultiplier(final UUID uuid) {
        return roleMultiplier(uuid, "core_money_multiplier");
    }

    /** Bonus Core Sky Token multiplier from the player's active role perks (1.0 = none). */
    public double coreTokenMultiplier(final UUID uuid) {
        return roleMultiplier(uuid, "core_token_multiplier");
    }

    /** Bonus sell-value / economy multiplier from the player's active role perks (1.0 = none). */
    public double economyMultiplier(final UUID uuid) {
        return roleMultiplier(uuid, "economy_multiplier");
    }

    /** Bonus Omnitool tool-XP multiplier from the player's active role perks (1.0 = none). */
    public double omnitoolXpMultiplier(final UUID uuid) {
        return roleMultiplier(uuid, "omnitool_xp_multiplier");
    }

    /** Bonus drop chance (0..1) from the player's active role perks. */
    public double dropChance(final UUID uuid) {
        return roleChance(uuid, "drop_chance");
    }

    /** Bonus rare-drop chance (0..1) from the player's active role perks. */
    public double rareDropChance(final UUID uuid) {
        return roleChance(uuid, "rare_drop_chance");
    }

    /** Auto-replant chance (0..1) for farming. */
    public double replantChance(final UUID uuid) {
        return roleChance(uuid, "replant_chance");
    }

    /** Tree-feller chance (0..1) for logging. */
    public double fellerChance(final UUID uuid) {
        return roleChance(uuid, "feller_chance");
    }

    /** Convenience for GUIs/messages: list of unlocked perk names for the active role. */
    public java.util.List<String> unlockedPerkNames(final UUID uuid) {
        final java.util.List<String> out = new java.util.ArrayList<>();
        final RoleManager.Role role = activeRole(uuid);
        if (role == null) return out;
        final int lvl = roles().getLevel(uuid, role.tree());
        for (final RolePerk p : registry().forRole(role.name())) {
            if (!p.hidden && p.unlockedAt(lvl)) out.add(p.name);
        }
        return out;
    }

    public RolePerk.Registry registryForGui() { return registry(); }

    /** Active display name of the player's role, or "None". */
    public String activeRoleDisplay(final UUID uuid) {
        final RoleManager.Role r = activeRole(uuid);
        return r == null ? "None" : r.display();
    }

    // helper used by callers that only have a Player
    public static UUID id(final Player p) { return p.getUniqueId(); }
}
