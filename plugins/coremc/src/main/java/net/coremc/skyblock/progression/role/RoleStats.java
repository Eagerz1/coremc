package net.coremc.skyblock.progression.role;

import net.coremc.coremc.CoreMC;
import net.coremc.skyblock.playerdata.PlayerData;
import net.coremc.skyblock.playerdata.PlayerDataManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Per-role activity statistics.
 *
 * <p>Statistics are stored in the permanent player data store under the key
 * {@code "<role>:<stat>"} (e.g. {@code mining:blocks}, {@code universal:xp}). They are
 * cumulative, never reset by a config reload or a role change, and live independently
 * per role — matching the existing per-role XP design.</p>
 *
 * <p>Standard stat keys (use these consistently so the future Quest system can read them):</p>
 * <ul>
 *   <li>{@code blocks}    — relevant blocks broken</li>
 *   <li>{@code logs}      — logs broken</li>
 *   <li>{@code crops}     — crops harvested</li>
 *   <li>{@code fish}      — fish caught</li>
 *   <li>{@code mobs}      — mobs killed</li>
 *   <li>{@code xp}        — role XP earned</li>
 *   <li>{@code core}      — Core contribution generated</li>
 *   <li>{@code tokens}    — Sky Tokens earned</li>
 * </ul>
 *
 * <p>These are exposed read-only snapshots for GUIs and the (future) Quest system via the
 * {@code RoleQuery} hooks; the canonical source is always the persisted player data.</p>
 */
public final class RoleStats {

    private final JavaPlugin plugin;

    public RoleStats(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private PlayerDataManager pdm() {
        return CoreMC.getInstance().playerDataManager();
    }

    private static String key(final RoleManager.Role role, final String stat) {
        return role.name().toLowerCase(Locale.ROOT) + ":" + stat.toLowerCase(Locale.ROOT);
    }

    /** Increment a stat for a role by {@code amount} (no-op if amount <= 0). */
    public void add(final UUID uuid, final RoleManager.Role role, final String stat, final long amount) {
        if (amount <= 0) return;
        final PlayerData d = pdm().get(uuid);
        d.addRoleStat(key(role, stat), amount);
        pdm().markDirty(uuid);
    }

    /** Read a stat for a role (0 if unset). */
    public long get(final UUID uuid, final RoleManager.Role role, final String stat) {
        return pdm().get(uuid).getRoleStat(key(role, stat));
    }

    /** Snapshot of all stats for a role. */
    public Map<String, Long> snapshot(final UUID uuid, final RoleManager.Role role) {
        final Map<String, Long> out = new LinkedHashMap<>();
        final String prefix = role.name().toLowerCase(Locale.ROOT) + ":";
        for (final var e : pdm().get(uuid).allRoleStats().entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                out.put(e.getKey().substring(prefix.length()), e.getValue());
            }
        }
        return out;
    }
}
