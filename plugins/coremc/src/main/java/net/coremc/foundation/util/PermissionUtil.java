package net.coremc.foundation.util;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Permission utilities.
 */
public final class PermissionUtil {

    public static final String PREFIX = "coremc.";

    private PermissionUtil() {}

    public static @NotNull String perm(final @NotNull String node) {
        return PREFIX + node;
    }

    /** Has permission, with a relaxed wildcard check (coremc.* grants all). */
    public static boolean has(final @Nullable Player player, final @NotNull String node) {
        if (player == null) {
            return false;
        }
        if (player.isOp() || player.hasPermission(PREFIX + "*")) {
            return true;
        }
        return player.hasPermission(node);
    }

    /** Has permission OR an explicit bypass node. */
    public static boolean hasAny(final @Nullable Player player, final @NotNull String... nodes) {
        for (final String n : nodes) {
            if (has(player, n)) {
                return true;
            }
        }
        return false;
    }

    /** Convenience: node + ".admin" bypass variant. */
    public static boolean hasOrAdmin(final @Nullable Player player, final @NotNull String node) {
        return has(player, node) || has(player, node + ".admin") || has(player, node + ".*");
    }

    public static @NotNull String lower(final @NotNull String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
