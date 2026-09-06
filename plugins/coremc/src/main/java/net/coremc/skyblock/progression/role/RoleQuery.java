package net.coremc.skyblock.progression.role;

import net.coremc.coremc.CoreMC;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Read-only query API for the future Quest system (and any external module).
 *
 * <p>Exposes clean, typed access to a player's role progression without exposing any
 * mutating logic. Everything here reads the canonical sources: {@link RoleManager} for
 * XP/levels, {@link RoleStats} for per-role statistics. No state is cached or duplicated.</p>
 */
public final class RoleQuery {

    private RoleQuery() {}

    public static int level(final Player p, final RoleManager.Role role) {
        return CoreMC.getInstance().progression().roles().getLevel(p.getUniqueId(), role.tree());
    }

    public static int level(final UUID uuid, final RoleManager.Role role) {
        return CoreMC.getInstance().progression().roles().getLevel(uuid, role.tree());
    }

    public static long xp(final Player p, final RoleManager.Role role) {
        return CoreMC.getInstance().progression().roles().getXp(p.getUniqueId(), role.tree());
    }

    public static long xp(final UUID uuid, final RoleManager.Role role) {
        return CoreMC.getInstance().progression().roles().getXp(uuid, role.tree());
    }

    public static long stat(final Player p, final RoleManager.Role role, final String stat) {
        return CoreMC.getInstance().progression().roleProgression().stats().get(p.getUniqueId(), role, stat);
    }

    public static long stat(final UUID uuid, final RoleManager.Role role, final String stat) {
        return CoreMC.getInstance().progression().roleProgression().stats().get(uuid, role, stat);
    }

    public static Map<String, Long> stats(final Player p, final RoleManager.Role role) {
        return CoreMC.getInstance().progression().roleProgression().stats().snapshot(p.getUniqueId(), role);
    }

    public static RoleManager.Role activeRole(final Player p) {
        return CoreMC.getInstance().progression().roles().get(p.getUniqueId());
    }
}
