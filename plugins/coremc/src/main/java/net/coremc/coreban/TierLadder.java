package net.coremc.coreban;

import net.coremc.coreban.model.PunishmentType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the ladder of durations for a given punishment type + tier from config.
 * Returns the list of durations (seconds); -1 = permanent; 0 = instant (warn/kick).
 */
public final class TierLadder {

    private TierLadder() {}

    public static List<Long> ladder(final org.bukkit.plugin.Plugin plugin,
                                    final PunishmentType type, final int tier) {
        final ConfigurationSection sec = plugin.getConfig()
                .getConfigurationSection("tiers." + type.name().toLowerCase() + ".t" + tier);
        final List<Long> out = new ArrayList<>();
        if (sec == null) {
            return out;
        }
        for (final String k : sec.getKeys(false)) {
            final String v = sec.getString(k);
            out.add(parseDuration(v));
        }
        return out;
    }

    /** Parse a duration string like "1m", "1d", "permanent" into seconds. -1 = permanent. */
    public static long parseDuration(final String input) {
        if (input == null) {
            return 0;
        }
        final String s = input.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.equals("permanent") || s.equals("perm")) {
            return -1;
        }
        long total = 0;
        final java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+)\\s*(s|m|h|d|w)").matcher(s);
        while (m.find()) {
            final long n = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "s" -> n;
                case "m" -> n * 60;
                case "h" -> n * 3600;
                case "d" -> n * 86400;
                case "w" -> n * 604800;
                default -> 0;
            };
        }
        return total;
    }

    public static String formatDuration(final long seconds) {
        if (seconds == -1) {
            return "permanent";
        }
        if (seconds == 0) {
            return "instant";
        }
        final long d = seconds / 86400;
        final long h = (seconds % 86400) / 3600;
        final long m = (seconds % 3600) / 60;
        final long s = seconds % 60;
        final StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
