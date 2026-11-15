package parking.backend;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns the raw slot_event rows (one row per status CHANGE) into the numbers an admin wants:
 * occupancy %, arrivals, stay lengths, busiest slot, peak hour, arrivals per day.
 *
 * Plain Java with no Spring or database types, so it can be tested on its own.
 *
 * How it reads the data: a slot keeps its last status until the next change, so each slot becomes
 * a timeline of "vacant" and "occupied" stretches. Repeated rows with the same status (for example
 * the full frame the backend records after a restart) are ignored. The time before the first
 * event ever recorded is not counted, and the last stretch runs until "now".
 */
public final class Analytics {

    private Analytics() { }

    /** One recorded change: slot number (1 = P1), new status, when. */
    public record Ev(int slot, boolean occupied, Instant at) { }

    public record SlotStats(String slot, int arrivals, long occupiedSeconds, double occupancyPct,
                            int completedStays, long avgStaySeconds, long longestStaySeconds, boolean occupiedNow) { }

    public record HourStat(int hour, double occupancyPct) { }

    public record DayStat(String date, int arrivals) { }

    public record Summary(int days, String from, String to, int totalEvents, int slotsSeen,
                          int occupiedNow, double occupancyNowPct, double avgOccupancyPct,
                          int totalArrivals, long avgStaySeconds, String busiestSlot, Integer peakHour) { }

    public record Result(Summary summary, List<SlotStats> slots, List<HourStat> hourly, List<DayStat> daily) { }

    /** Mutable per-slot accumulator used while walking the events. */
    private static final class Acc {
        int arrivals;
        long occupiedMillis;
        final List<Long> stays = new ArrayList<>();   // completed stays that ended inside the window, in seconds
        boolean occupiedNow;
    }

    public static Result compute(List<Ev> events, Instant now, int days, ZoneId zone) {
        if (events.isEmpty()) {
            Summary s = new Summary(days, null, now.toString(), 0, 0, 0, 0, 0, 0, 0, null, null);
            return new Result(s, List.of(), emptyHours(), List.of());
        }

        List<Ev> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparing(Ev::at));
        Instant dataStart = sorted.get(0).at();
        Instant from = now.minus(Duration.ofDays(days));
        if (from.isBefore(dataStart)) from = dataStart;        // do not count time before any data existed
        if (from.isAfter(now)) from = now;

        Map<Integer, List<Ev>> bySlot = new TreeMap<>();
        for (Ev e : sorted) bySlot.computeIfAbsent(e.slot(), k -> new ArrayList<>()).add(e);

        double[] hourOccupied = new double[24];                 // occupied slot-seconds per hour of day
        double[] hourWindow = new double[24];                   // window seconds per hour of day
        distribute(from, now, zone, hourWindow);

        Map<LocalDate, Integer> daily = new LinkedHashMap<>();  // every day in the window, even empty ones
        for (LocalDate d = from.atZone(zone).toLocalDate(); !d.isAfter(now.atZone(zone).toLocalDate()); d = d.plusDays(1)) {
            daily.put(d, 0);
        }

        Map<Integer, Acc> accs = new TreeMap<>();
        for (Map.Entry<Integer, List<Ev>> entry : bySlot.entrySet()) {
            Acc acc = new Acc();
            accs.put(entry.getKey(), acc);

            Boolean state = null;                               // unknown until the first event
            Instant start = null;
            for (Ev e : entry.getValue()) {
                if (state == null) { state = e.occupied(); start = e.at(); continue; }
                if (e.occupied() == state) continue;            // same status again, not a real change

                if (state) {                                    // occupied -> vacant: a stay just ended
                    addOccupied(acc, start, e.at(), from, now, zone, hourOccupied);
                    if (!e.at().isBefore(from) && !e.at().isAfter(now)) {
                        acc.stays.add(Math.max(0, Duration.between(start, e.at()).getSeconds()));
                    }
                } else if (!e.at().isBefore(from) && !e.at().isAfter(now)) {   // vacant -> occupied: an arrival
                    acc.arrivals++;
                    daily.merge(e.at().atZone(zone).toLocalDate(), 1, Integer::sum);
                }
                state = e.occupied();
                start = e.at();
            }
            if (state) addOccupied(acc, start, now, from, now, zone, hourOccupied);   // still occupied right now
            acc.occupiedNow = state;
        }

        long spanMillis = Math.max(0, Duration.between(from, now).toMillis());
        int slotsSeen = accs.size();

        List<SlotStats> slots = new ArrayList<>();
        long totalOccMillis = 0, totalStaySeconds = 0;
        int totalArrivals = 0, totalStays = 0, occupiedNow = 0;
        for (Map.Entry<Integer, Acc> entry : accs.entrySet()) {
            Acc a = entry.getValue();
            long staySum = a.stays.stream().mapToLong(Long::longValue).sum();
            long longest = a.stays.stream().mapToLong(Long::longValue).max().orElse(0);
            slots.add(new SlotStats("P" + entry.getKey(), a.arrivals, a.occupiedMillis / 1000,
                    pct(a.occupiedMillis, spanMillis), a.stays.size(),
                    a.stays.isEmpty() ? 0 : staySum / a.stays.size(), longest, a.occupiedNow));
            totalOccMillis += a.occupiedMillis;
            totalStaySeconds += staySum;
            totalStays += a.stays.size();
            totalArrivals += a.arrivals;
            if (a.occupiedNow) occupiedNow++;
        }

        List<HourStat> hourly = new ArrayList<>();
        Integer peakHour = null;
        double peak = 0;
        for (int h = 0; h < 24; h++) {
            double denominator = hourWindow[h] * slotsSeen;
            double p = denominator <= 0 ? 0 : round1(hourOccupied[h] / denominator * 100);
            hourly.add(new HourStat(h, p));
            if (p > peak) { peak = p; peakHour = h; }
        }

        SlotStats busiest = slots.stream()
                .filter(s -> s.arrivals() > 0 || s.occupiedSeconds() > 0)
                .max(Comparator.comparingInt(SlotStats::arrivals).thenComparingLong(SlotStats::occupiedSeconds))
                .orElse(null);

        List<DayStat> dayStats = new ArrayList<>();
        daily.forEach((d, n) -> dayStats.add(new DayStat(d.toString(), n)));

        Summary summary = new Summary(days, from.toString(), now.toString(), sorted.size(), slotsSeen,
                occupiedNow, slotsSeen == 0 ? 0 : round1(occupiedNow * 100.0 / slotsSeen),
                pct(totalOccMillis, spanMillis * slotsSeen), totalArrivals,
                totalStays == 0 ? 0 : totalStaySeconds / totalStays,
                busiest == null ? null : busiest.slot(), peakHour);
        return new Result(summary, slots, hourly, dayStats);
    }

    /** Adds the part of [start, end) that lies inside the window to the slot total and the hour-of-day buckets. */
    private static void addOccupied(Acc acc, Instant start, Instant end, Instant from, Instant now,
                                    ZoneId zone, double[] hourOccupied) {
        Instant s = start.isBefore(from) ? from : start;
        Instant e = end.isAfter(now) ? now : end;
        if (!s.isBefore(e)) return;
        acc.occupiedMillis += Duration.between(s, e).toMillis();
        distribute(s, e, zone, hourOccupied);
    }

    /** Splits [start, end) at local hour boundaries and adds the seconds to buckets[hour of day]. */
    private static void distribute(Instant start, Instant end, ZoneId zone, double[] buckets) {
        Instant cur = start;
        while (cur.isBefore(end)) {
            ZonedDateTime z = cur.atZone(zone);
            Instant next = z.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant();
            if (next.isAfter(end)) next = end;
            buckets[z.getHour()] += Duration.between(cur, next).toMillis() / 1000.0;
            cur = next;
        }
    }

    private static List<HourStat> emptyHours() {
        List<HourStat> list = new ArrayList<>();
        for (int h = 0; h < 24; h++) list.add(new HourStat(h, 0));
        return list;
    }

    private static double pct(long part, long whole) {
        return whole <= 0 ? 0 : round1(part * 100.0 / whole);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
