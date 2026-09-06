package net.coremc.foundation.util;
import net.coremc.foundation.CoreFoundation;


import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Player lookup and permission helpers.
 */
public final class PlayerUtil {

    private PlayerUtil() {}

    /** Resolve a player by exact name (online or offline cache). */
    public static @Nullable Player getPlayer(final @NotNull String name) {
        return Bukkit.getPlayerExact(name);
    }

    /** Resolve an offline player by name. */
    public static @NotNull OfflinePlayer getOfflinePlayer(final @NotNull String name) {
        return Bukkit.getOfflinePlayer(name);
    }

    /** True if the player has the permission (or is console/op-level bypass). */
    public static boolean hasPermission(final @Nullable Player player, final @NotNull String permission) {
        return player != null && player.hasPermission(permission);
    }

    /**
     * Check a permission and send the configured no-permission message if missing.
     * @return true if the player has permission.
     */
    public static boolean checkPermission(final @Nullable Player player,
                                          final @NotNull String permission) {
        if (hasPermission(player, permission)) {
            return true;
        }
        if (player != null) {
            final java.util.Map<String, String> vars = new java.util.HashMap<>();
            vars.put("permission", permission);
            CoreFoundation.getInstance().messages().send(player, "messages.no-permission", vars);
        }
        return false;
    }
}
