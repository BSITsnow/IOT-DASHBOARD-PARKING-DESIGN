package parking.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
public class FrameController {

    private final SlotEventRepository repository;
    private final ParkingSessionRepository sessionRepository;
    private final int totalSlots;
    private volatile String latestFrame = "";
    private volatile long updatedAtMillis = 0;
    private boolean[] previous = null;

    public FrameController(SlotEventRepository repository,
                           ParkingSessionRepository sessionRepository,
                           @Value("${parking.total-slots:2}") int totalSlots) {
        this.repository = repository;
        this.sessionRepository = sessionRepository;
        this.totalSlots = totalSlots;
    }

    @PostMapping(value = "/frame", consumes = MediaType.TEXT_PLAIN_VALUE)
    public synchronized ResponseEntity<Void> receive(@RequestBody String frame) {
        boolean[] current = parse(frame);
        if (current == null || current.length != totalSlots) {
            return ResponseEntity.badRequest().build();
        }

        Instant now = Instant.now();
        for (int i = 0; i < current.length; i++) {
            boolean changed = previous == null
                    || i >= previous.length
                    || previous[i] != current[i];
            if (changed) {
                repository.save(new SlotEvent(i + 1, current[i], now));

                if (current[i]) {
                    // car arrived: close any leftover open session, then open a new one
                    sessionRepository.findBySlotNumberAndEndedAtIsNull(i + 1).ifPresent(s -> {
                        s.close(now);
                        sessionRepository.save(s);
                    });
                    sessionRepository.save(new ParkingSession(i + 1, now));
                } else {
                    // car left: close the open session if there is one
                    sessionRepository.findBySlotNumberAndEndedAtIsNull(i + 1).ifPresent(s -> {
                        s.close(now);
                        sessionRepository.save(s);
                    });
                }
            }
        }
        previous = current;

        latestFrame = frame.trim();
        updatedAtMillis = System.currentTimeMillis();
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/frame", produces = MediaType.TEXT_PLAIN_VALUE)
    public String latest() {
        boolean tooOld = System.currentTimeMillis() - updatedAtMillis > 3_000;
        return tooOld ? "" : latestFrame;
    }

    @GetMapping("/events")
    public List<SlotEvent> events(@RequestParam(defaultValue = "50") int limit) {
        int size = Math.min(Math.max(limit, 1), 500);
        return repository.findAllByOrderByRecordedAtDesc(PageRequest.of(0, size));
    }

    private static boolean[] parse(String text) {
        String s = text.trim();
        if (s.startsWith("S:") || s.startsWith("s:")) s = s.substring(2);
        s = s.replace(",", "").replace(" ", "");
        if (s.isEmpty() || !s.matches("[01]+")) return null;
        boolean[] result = new boolean[s.length()];
        for (int i = 0; i < s.length(); i++) {
            result[i] = s.charAt(i) == '1';
        }
        return result;
    }
}