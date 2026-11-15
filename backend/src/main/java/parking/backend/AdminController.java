package parking.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record SessionDto(Long id, int slotNumber, Instant startedAt, Instant endedAt,
                             long durationSeconds, boolean active) { }

    public record SlotStatus(int slotNumber, boolean occupied, Instant since) { }

    public record Summary(int totalSlots, int occupiedNow, int availableNow,
                          int sessionsToday, long avgStaySeconds, long longestStaySeconds) { }

    private final ParkingSessionRepository sessions;
    private final int totalSlots;

    public AdminController(ParkingSessionRepository sessions,
                           @Value("${parking.total-slots:4}") int totalSlots) {
        this.sessions = sessions;
        this.totalSlots = totalSlots;
    }

    @GetMapping("/sessions")
    public List<SessionDto> sessions(@RequestParam(required = false) Integer slot,
                                     @RequestParam(defaultValue = "50") int limit) {
        int size = Math.min(Math.max(limit, 1), 500);
        PageRequest page = PageRequest.of(0, size);
        List<ParkingSession> rows = (slot == null)
                ? sessions.findAllByOrderByStartedAtDesc(page)
                : sessions.findBySlotNumberOrderByStartedAtDesc(slot, page);
        return rows.stream().map(AdminController::toDto).toList();
    }

    @GetMapping("/slots")
    public List<SlotStatus> slots() {
        Map<Integer, ParkingSession> open = new HashMap<>();
        for (ParkingSession s : sessions.findByEndedAtIsNull()) {
            open.put(s.getSlotNumber(), s);
        }
        List<SlotStatus> result = new ArrayList<>();
        for (int n = 1; n <= totalSlots; n++) {
            ParkingSession s = open.get(n);
            result.add(new SlotStatus(n, s != null, s == null ? null : s.getStartedAt()));
        }
        return result;
    }

    @GetMapping("/summary")
    public Summary summary() {
        int occupiedNow = (int) sessions.findByEndedAtIsNull().stream()
                .map(ParkingSession::getSlotNumber).distinct().count();

        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<ParkingSession> today = sessions.findByStartedAtGreaterThanEqual(startOfDay);

        List<Long> finished = today.stream()
                .filter(s -> s.getEndedAt() != null)
                .map(s -> Duration.between(s.getStartedAt(), s.getEndedAt()).getSeconds())
                .toList();

        long avg = finished.isEmpty() ? 0
                : (long) finished.stream().mapToLong(Long::longValue).average().orElse(0);
        long longest = finished.stream().mapToLong(Long::longValue).max().orElse(0);

        return new Summary(totalSlots, occupiedNow, totalSlots - occupiedNow,
                today.size(), avg, longest);
    }

    private static SessionDto toDto(ParkingSession s) {
        boolean active = s.getEndedAt() == null;
        Instant end = active ? Instant.now() : s.getEndedAt();
        return new SessionDto(s.getId(), s.getSlotNumber(), s.getStartedAt(), s.getEndedAt(),
                Duration.between(s.getStartedAt(), end).getSeconds(), active);
    }
}