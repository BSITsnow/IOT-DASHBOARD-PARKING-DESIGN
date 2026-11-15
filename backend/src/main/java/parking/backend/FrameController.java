package parking.backend;

import org.springframework.data.domain.PageRequest;                  // builds a "first N rows" request
import org.springframework.http.MediaType;                           // constants such as "text/plain"
import org.springframework.http.ResponseEntity;                      // lets us choose the HTTP status code
import org.springframework.web.bind.annotation.*;                    // @RestController, @PostMapping, etc.

import java.time.Instant;
import java.util.List;

@RestController                                                      // this class answers web requests and returns data
@RequestMapping("/api")                                              // every URL starts with /api
public class FrameController {

    private final SlotEventRepository repository;                    // our connection to the database
    private volatile String latestFrame = "";                        // newest frame text, e.g. "S:1,0"
    private volatile long updatedAtMillis = 0;                       // when that frame arrived
    private boolean[] previous = null;                               // the last frame as booleans, used to detect changes

    public FrameController(SlotEventRepository repository) {        // Spring passes the repository in automatically
        this.repository = repository;
    }

    @PostMapping(value = "/frame", consumes = MediaType.TEXT_PLAIN_VALUE)    // POST /api/frame with a plain text body
    public synchronized ResponseEntity<Void> receive(@RequestBody String frame) {   // synchronized: one request at a time
        boolean[] current = parse(frame);                            // turn the text into true/false values
        if (current == null) {                                       // not a valid frame
            return ResponseEntity.badRequest().build();              // reply with HTTP 400 so the sender knows
        }

        Instant now = Instant.now();                                 // one timestamp for all changes in this frame
        for (int i = 0; i < current.length; i++) {                   // check every slot
            boolean changed = previous == null                       // first frame since startup, record everything
                    || i >= previous.length                          // the slot count grew
                    || previous[i] != current[i];                    // this slot's status differs from last time
            if (changed) {
                repository.save(new SlotEvent(i + 1, current[i], now));   // insert one row, slot numbers start at 1
            }
        }
        previous = current;                                          // remember this frame for the next comparison

        latestFrame = frame.trim();                                  // keep the text for the dashboard to read
        updatedAtMillis = System.currentTimeMillis();                // remember when it arrived
        return ResponseEntity.ok().build();                          // reply with HTTP 200
    }

    @GetMapping(value = "/frame", produces = MediaType.TEXT_PLAIN_VALUE)    // GET /api/frame, used by the dashboard
    public String latest() {
        boolean tooOld = System.currentTimeMillis() - updatedAtMillis > 3_000;   // older than 3 seconds counts as lost
        return tooOld ? "" : latestFrame;                            // an empty reply makes the dashboard show stale data
    }

    @GetMapping("/events")                                           // GET /api/events returns JSON history
    public List<SlotEvent> events(@RequestParam(defaultValue = "50") int limit) {   // optional ?limit=...
        int size = Math.min(Math.max(limit, 1), 500);                // keep the limit between 1 and 500
        return repository.findAllByOrderByRecordedAtDesc(PageRequest.of(0, size));   // newest rows first
    }

    private static boolean[] parse(String text) {                    // converts "S:1,0" into {true, false}
        String s = text.trim();                                      // remove spaces and newlines at the ends
        if (s.startsWith("S:") || s.startsWith("s:")) s = s.substring(2);   // remove the optional prefix
        s = s.replace(",", "").replace(" ", "");                     // remove commas and spaces
        if (s.isEmpty() || !s.matches("[01]+")) return null;         // must be only 1s and 0s, otherwise invalid
        boolean[] result = new boolean[s.length()];                  // one entry per slot
        for (int i = 0; i < s.length(); i++) {
            result[i] = s.charAt(i) == '1';                          // '1' means occupied
        }
        return result;
    }
}