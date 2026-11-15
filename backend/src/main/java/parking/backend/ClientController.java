package parking.backend;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What a signed-in client may see: live availability only, no history, no accounts. */
@RestController
@RequestMapping("/api/client")
public class ClientController {

    public record SlotView(String id, boolean occupied) { }
    public record StatusView(boolean live, int total, int occupied, int available, int percent,
                             String updatedAt, List<SlotView> slots) { }

    private final FrameController frames;
    private final SlotEventRepository events;

    public ClientController(FrameController frames, SlotEventRepository events) {
        this.frames = frames;
        this.events = events;
    }

    @GetMapping("/status")
    public StatusView status() {
        boolean[] current = FrameController.parse(frames.latest());     // empty text = no frame in the last 3 s
        boolean live = current != null;
        Instant updated = live ? Instant.ofEpochMilli(frames.updatedAtMillis()) : null;

        if (!live) {                                                    // sensors silent: show the last known state
            List<SlotEvent> latest = events.findLatestPerSlot();
            current = new boolean[latest.size()];
            for (int i = 0; i < latest.size(); i++) {
                current[i] = latest.get(i).isOccupied();
                Instant at = latest.get(i).getRecordedAt();
                if (updated == null || at.isAfter(updated)) updated = at;
            }
        }

        List<SlotView> slots = new ArrayList<>();
        int occupied = 0;
        for (int i = 0; i < current.length; i++) {
            slots.add(new SlotView("P" + (i + 1), current[i]));
            if (current[i]) occupied++;
        }
        int total = current.length;
        int percent = total == 0 ? 0 : (int) Math.round(occupied * 100.0 / total);
        return new StatusView(live, total, occupied, total - occupied, percent,
                updated == null ? null : updated.toString(), slots);
    }
}
