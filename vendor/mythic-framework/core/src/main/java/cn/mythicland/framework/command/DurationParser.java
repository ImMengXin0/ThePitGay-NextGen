package cn.mythicland.framework.command;

public final class DurationParser {
    private DurationParser() {
    }

    public static long parseMillis(String text) {
        if (text == null) return -1L;
        String normalized = text.trim().toLowerCase();
        if (normalized.isEmpty()) return -1L;

        long multiplier = 1000L;
        if (normalized.endsWith("ms")) {
            multiplier = 1L;
            normalized = normalized.substring(0, normalized.length() - 2);
        } else if (normalized.endsWith("t")) {
            multiplier = 50L;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("s")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("m")) {
            multiplier = 60_000L;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("h")) {
            multiplier = 3_600_000L;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("d")) {
            multiplier = 86_400_000L;
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        try {
            double base = Double.parseDouble(normalized.trim());
            if (base <= 0.0D) return -1L;
            return Math.max(1L, Math.round(base * multiplier));
        } catch (NumberFormatException exception) {
            return -1L;
        }
    }

    public static String formatMillis(long millis) {
        return formatDurationToken(millis);
    }

    public static String formatDurationToken(long millis) {
        if (millis >= 86_400_000L && millis % 86_400_000L == 0L) return millis / 86_400_000L + "d";
        if (millis >= 3_600_000L && millis % 3_600_000L == 0L) return millis / 3_600_000L + "h";
        if (millis >= 60_000L && millis % 60_000L == 0L) return millis / 60_000L + "m";
        if (millis >= 1000L && millis % 1000L == 0L) return millis / 1000L + "s";
        if (millis >= 50L && millis % 50L == 0L) return millis / 50L + "t";
        return millis + "ms";
    }
}
