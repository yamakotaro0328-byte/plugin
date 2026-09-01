package com.yamakotaro.ecoban.core;

/**
 * Parses the "1d2h30m"-style durations used by /tempban and /tempmute on both platforms (s/m/h/d/w).
 */
public final class DurationParser {

    private DurationParser() {
    }

    /**
     * @return duration in millis; 0 for permanent ("permanent"/"perm"/"-1"); -1 if the input
     * couldn't be parsed at all.
     */
    public static long parseMillis(String input) {
        if (input == null) {
            return -1;
        }
        String trimmed = input.trim().toLowerCase();
        if (trimmed.equals("permanent") || trimmed.equals("perm") || trimmed.equals("-1")) {
            return 0;
        }
        long totalMillis = 0;
        StringBuilder number = new StringBuilder();
        boolean matchedAny = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isDigit(c)) {
                number.append(c);
                continue;
            }
            if (number.length() == 0) {
                return -1;
            }
            long value = Long.parseLong(number.toString());
            number.setLength(0);
            long unitMillis = switch (c) {
                case 's' -> 1000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                case 'w' -> 604_800_000L;
                default -> -1L;
            };
            if (unitMillis < 0) {
                return -1;
            }
            totalMillis += value * unitMillis;
            matchedAny = true;
        }
        if (number.length() > 0 || !matchedAny) {
            return -1;
        }
        return totalMillis;
    }
}
