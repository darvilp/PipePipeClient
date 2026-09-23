package org.schabi.newpipe.util;

import java.util.Locale;

/** Exact, bounded elapsed times for the SponsorBlock editor (not clock times). */
public final class SponsorBlockTime {
    private SponsorBlockTime() { }

    public static int parse(final String input) {
        final String text = input.trim().replace(',', '.');
        if (!text.matches("[0-9]+(?::[0-9]{1,2}){0,2}(?:\\.[0-9]{1,3})?")) {
            throw new IllegalArgumentException("Invalid timestamp");
        }
        final String[] fraction = text.split("\\.", -1);
        final String[] parts = fraction[0].split(":");
        long seconds = 0;
        try {
            for (int i = 0; i < parts.length; i++) {
                final long value = Long.parseLong(parts[i]);
                if (i > 0 && value >= 60) {
                    throw new IllegalArgumentException("Minutes and seconds must be below 60");
                }
                seconds = Math.addExact(Math.multiplyExact(seconds, 60), value);
            }
            final int millis = fraction.length == 1 ? 0
                    : Integer.parseInt((fraction[1] + "000").substring(0, 3));
            return Math.toIntExact(Math.addExact(Math.multiplyExact(seconds, 1000), millis));
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Timestamp is too large", exception);
        }
    }

    public static String format(final long millis) {
        final long seconds = millis / 1000;
        return String.format(Locale.ROOT, "%d:%02d:%02d.%03d",
                seconds / 3600, seconds / 60 % 60, seconds % 60, millis % 1000);
    }

    public static boolean valid(final Integer start, final Integer end,
                                final boolean highlight, final Long duration) {
        return start != null && start >= 0
                && (duration == null || start <= duration)
                && (highlight || end != null && end > start
                && (duration == null || end <= duration));
    }
}
