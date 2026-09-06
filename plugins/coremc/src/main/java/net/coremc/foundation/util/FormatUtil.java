package net.coremc.foundation.util;

/** Simple formatting utilities. */
public final class FormatUtil {
    private FormatUtil() {}

    public static String formatNumber(long number) {
        return String.format(java.util.Locale.ROOT, ",%d", number);
    }

    public static String formatNumber(double number) {
        return String.format(java.util.Locale.ROOT, ",.2f", number);
    }

    public static String formatTokens(long tokens) {
        return formatNumber(tokens);
    }

    /** Format a duration in seconds as "Xm Ys". */
    public static String formatTime(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        final long m = seconds / 60;
        final long s = seconds % 60;
        if (m > 0) {
            return m + "m " + s + "s";
        }
        return s + "s";
    }
}