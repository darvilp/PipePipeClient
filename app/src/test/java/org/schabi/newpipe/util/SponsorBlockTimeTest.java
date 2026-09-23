package org.schabi.newpipe.util;

import static org.junit.Assert.*;
import org.junit.Test;

public class SponsorBlockTimeTest {
    @Test public void parsesZeroAndFractionalTimesWithoutRounding() {
        assertEquals(0, SponsorBlockTime.parse("0:00"));
        assertEquals(62345, SponsorBlockTime.parse("1:02.345"));
        assertEquals(3600100, SponsorBlockTime.parse("1:00:00.1"));
        assertEquals(12340, SponsorBlockTime.parse(" 12,34 "));
    }
    @Test public void editingRoundTripsMillisecondsAndLongDurations() {
        for (int time : new int[]{0, 1, 999, 60345, 90000123, Integer.MAX_VALUE}) {
            assertEquals(time, SponsorBlockTime.parse(SponsorBlockTime.format(time)));
        }
        assertEquals("25:00:00.123", SponsorBlockTime.format(90000123));
        assertEquals("25:00:00", TimeUtils.millisecondsToString(90000000));
    }
    @Test public void invalidOrOverflowingInputIsRejected() {
        for (String text : new String[]{"", "-1", "1:60", "1:99:00", "1.0001", "1:",
                "1:2:3:4", "2147483648", "9223372036854775807:00", "NaN"}) {
            assertThrows(text, IllegalArgumentException.class, () -> SponsorBlockTime.parse(text));
        }
    }
    @Test public void rangeAndHighlightHaveDifferentRequirements() {
        assertTrue(SponsorBlockTime.valid(0, 1000, false, 1000L));
        assertFalse(SponsorBlockTime.valid(null, 1000, false, null));
        assertFalse(SponsorBlockTime.valid(0, null, false, null));
        assertFalse(SponsorBlockTime.valid(1000, 1000, false, null));
        assertFalse(SponsorBlockTime.valid(2000, 1000, false, null));
        assertFalse(SponsorBlockTime.valid(0, 1001, false, 1000L));
        assertTrue(SponsorBlockTime.valid(1000, null, true, 1000L));
        assertFalse(SponsorBlockTime.valid(1001, null, true, 1000L));
        assertTrue(SponsorBlockTime.valid(0, 1000, false, null));
    }
}
