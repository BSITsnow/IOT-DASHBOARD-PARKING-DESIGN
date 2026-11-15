package parking.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

class AnalyticsTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    private static Analytics.Ev ev(int slot, boolean occ, String at) {
        return new Analytics.Ev(slot, occ, Instant.parse(at));
    }

    @Test
    void emptyDataGivesZeros() {
        Analytics.Result r = Analytics.compute(List.of(), NOW, 7, UTC);
        assertEquals(0, r.summary().slotsSeen());
        assertEquals(24, r.hourly().size());
        assertNull(r.summary().peakHour());
    }

    @Test
    void stayArrivalAndOccupancy() {
        // P1: vacant 08:00, occupied 09:00, vacant 10:00 (1 h stay), occupied again 11:00 until now (12:00)
        // P2: occupied from 08:00 and never changes
        List<Analytics.Ev> events = List.of(
                ev(1, false, "2026-11-15T08:00:00Z"),
                ev(2, true,  "2026-11-15T08:00:00Z"),
                ev(1, true,  "2026-11-15T09:00:00Z"),
                ev(1, true,  "2026-11-15T09:30:00Z"),   // duplicate status, must be ignored
                ev(1, false, "2026-11-15T10:00:00Z"),
                ev(1, true,  "2026-11-15T11:00:00Z"));
        Analytics.Result r = Analytics.compute(events, NOW, 7, UTC);

        Analytics.SlotStats p1 = r.slots().get(0);
        assertEquals("P1", p1.slot());
        assertEquals(2, p1.arrivals());                       // 09:00 and 11:00
        assertEquals(2 * 3600, p1.occupiedSeconds());         // 09-10 and 11-12
        assertEquals(50.0, p1.occupancyPct());                // 2 h of the 4 h window
        assertEquals(1, p1.completedStays());
        assertEquals(3600, p1.avgStaySeconds());
        assertTrue(p1.occupiedNow());

        Analytics.SlotStats p2 = r.slots().get(1);
        assertEquals(0, p2.arrivals());                       // first row is not an observed arrival
        assertEquals(100.0, p2.occupancyPct());

        assertEquals(2, r.summary().slotsSeen());
        assertEquals(2, r.summary().occupiedNow());
        assertEquals(75.0, r.summary().avgOccupancyPct());    // (2 h + 4 h) of 8 slot-hours
        assertEquals(2, r.summary().totalArrivals());
        assertEquals("P1", r.summary().busiestSlot());
    }

    @Test
    void hourlyBucketsUseTheGivenTimeZone() {
        List<Analytics.Ev> events = List.of(
                ev(1, false, "2026-11-15T08:00:00Z"),
                ev(1, true,  "2026-11-15T09:00:00Z"),
                ev(1, false, "2026-11-15T10:00:00Z"));
        Analytics.Result utc = Analytics.compute(events, NOW, 7, UTC);
        assertEquals(100.0, utc.hourly().get(9).occupancyPct());
        assertEquals(0.0, utc.hourly().get(8).occupancyPct());
        assertEquals(Integer.valueOf(9), utc.summary().peakHour());

        Analytics.Result manila = Analytics.compute(events, NOW, 7, ZoneId.of("Asia/Manila"));   // UTC+8
        assertEquals(100.0, manila.hourly().get(17).occupancyPct());
        assertEquals(Integer.valueOf(17), manila.summary().peakHour());
    }

    @Test
    void windowClipsOldActivity() {
        List<Analytics.Ev> events = List.of(
                ev(1, false, "2026-11-01T00:00:00Z"),
                ev(1, true,  "2026-11-01T01:00:00Z"),
                ev(1, false, "2026-11-01T02:00:00Z"),     // far outside a 1-day window
                ev(1, true,  "2026-11-15T06:00:00Z"));
        Analytics.Result r = Analytics.compute(events, NOW, 1, UTC);
        assertEquals(1, r.slots().get(0).arrivals());
        assertEquals(6 * 3600, r.slots().get(0).occupiedSeconds());
        assertEquals(0, r.slots().get(0).completedStays());       // the old stay ended before the window
        assertEquals(2, r.daily().size());                        // 14th and 15th
    }
}
