package net.coremc.coreban.command;
import net.coremc.coremc.CoreMC;
import org.bukkit.plugin.java.JavaPlugin;


import net.coremc.coreban.TierLadder;
import net.coremc.coreban.model.Punishment;
import net.coremc.coreban.model.PunishmentType;
import net.coremc.coreban.storage.PunishmentStorage;
import net.coremc.foundation.CoreFoundation;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Shared helpers for punishment commands.
 */
public final class CommandUtil {

    private CommandUtil() {}

    public static boolean hasTierPerm(final Player staff, final PunishmentType type, final int tier) {
        final String node = "coreban." + type.name().toLowerCase() + ".t" + tier;
        return staff.hasPermission(node) || staff.hasPermission("coreban.ban.t" + tier)
                || staff.hasPermission("coreban.*");
    }

    /** Resolve a target player by name (online or offline). */
    public static OfflinePlayer resolve(final String name) {
        final Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayer(name);
    }

    /**
     * Build the next punishment for a tier based on the player's prior offence count.
     * Applies the punishment (ban/kick) or just records (warn/mute).
     */
    public static Punishment apply(final JavaPlugin plugin, final PunishmentType type,
                                   final UUID target, final int tier,
                                   final String reason, final String staffName,
                                   final long nowSeconds) {
        final PunishmentStorage storage = CoreMC.getInstance().ban().storage();
        final int offence = storage.offenceCount(target, type, tier) + 1;
        final var ladder = TierLadder.ladder(plugin, type, tier);
        final long duration = ladder.isEmpty() ? 0 : ladder.get(Math.min(offence, ladder.size()) - 1);
        final long expiredAt = duration > 0 ? nowSeconds + duration : 0;
        final Punishment p = new Punishment(0, target, type, tier, offence, duration,
                reason, staffName, nowSeconds, false, expiredAt);
        return storage.insert(p);
    }

    public static void announce(final JavaPlugin plugin, final Punishment p) {
        final CoreFoundation cf = CoreFoundation.getInstance();
        final String dur = TierLadder.formatDuration(p.durationSeconds());
        final String msg = cf.messages().getPrefix()
                + " <red>" + Bukkit.getOfflinePlayer(p.target()).getName()
                + " was " + p.type().name().toLowerCase() + "ed (T" + p.tier()
                + ", #" + p.offenceNumber() + ", " + dur + "): <white>" + p.reason();
        Bukkit.broadcast(msg, "coreban.notify");
    }
}
