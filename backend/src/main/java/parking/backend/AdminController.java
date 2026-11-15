package parking.backend;

<<<<<<< HEAD
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

=======
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

/** Everything under /api/admin. AuthFilter lets only signed-in ADMIN users reach it. */
>>>>>>> 9a650f05159ae8b62142f9780e931f06e227c939
@RestController
@RequestMapping("/api/admin")
public class AdminController {

<<<<<<< HEAD
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
=======
    public record CreateUser(String username, String password, String fullName, String role) { }
    public record UpdateUser(String fullName, String role, Boolean active, String password) { }
    public record LoginView(Long id, String username, boolean success, String ipAddress, String note, String loggedAt) { }

    private final AppUserRepository users;
    private final LoginLogRepository logins;
    private final SlotEventRepository events;
    private final ZoneId zone;

    public AdminController(AppUserRepository users, LoginLogRepository logins, SlotEventRepository events,
                           @Value("${parking.timezone:}") String timezone) {
        this.users = users;
        this.logins = logins;
        this.events = events;
        this.zone = timezone == null || timezone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(timezone);
    }

    // ---------- analyzed data ----------

    @GetMapping("/analytics")
    public Analytics.Result analytics(@RequestParam(defaultValue = "7") int days) {
        int window = Math.min(Math.max(days, 1), 90);
        List<Analytics.Ev> all = events.findAllByOrderByRecordedAtAsc().stream()
                .map(e -> new Analytics.Ev(e.getSlotNumber(), e.isOccupied(), e.getRecordedAt()))
                .toList();
        return Analytics.compute(all, Instant.now(), window, zone);
    }

    @GetMapping("/logins")
    public List<LoginView> loginHistory(@RequestParam(defaultValue = "50") int limit) {
        int size = Math.min(Math.max(limit, 1), 500);
        return logins.findAllByOrderByLoggedAtDesc(PageRequest.of(0, size)).stream()
                .map(l -> new LoginView(l.getId(), l.getUsername(), l.isSuccess(), l.getIpAddress(), l.getNote(),
                        l.getLoggedAt().toString()))
                .toList();
    }

    // ---------- user management ----------

    @GetMapping("/users")
    public List<UserView> listUsers() {
        return users.findAll(Sort.by("id")).stream().map(UserView::of).toList();
    }

    @PostMapping("/users")
    public ResponseEntity<UserView> createUser(@RequestBody CreateUser body) {
        String username = Rules.username(body.username());
        String password = Rules.password(body.password());
        AppUser.Role role = Rules.role(body.role() == null ? "CLIENT" : body.role());
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is taken");
        }
        AppUser saved = users.save(new AppUser(username, PasswordHasher.hash(password), Rules.fullName(body.fullName()), role));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserView.of(saved));
    }

    @PutMapping("/users/{id}")
    public UserView updateUser(@PathVariable Long id, @RequestBody UpdateUser body, HttpServletRequest request) {
        AppUser target = find(id);
        AppUser me = (AppUser) request.getAttribute(AuthFilter.USER_ATTR);

        AppUser.Role newRole = body.role() == null ? target.getRole() : Rules.role(body.role());
        boolean newActive = body.active() == null ? target.isActive() : body.active();
        boolean losesAdmin = target.getRole() == AppUser.Role.ADMIN && target.isActive()
                && (newRole != AppUser.Role.ADMIN || !newActive);

        if (target.getId().equals(me.getId()) && (newRole != target.getRole() || !newActive)) {
            throw Rules.bad("You cannot change your own role or disable your own account");
        }
        if (losesAdmin && users.countByRoleAndActive(AppUser.Role.ADMIN, true) <= 1) {
            throw Rules.bad("There must always be at least one active administrator");
        }

        target.setRole(newRole);
        target.setActive(newActive);
        if (body.fullName() != null) target.setFullName(Rules.fullName(body.fullName()));
        if (body.password() != null && !body.password().isEmpty()) {          // admin password reset
            target.setPasswordHash(PasswordHasher.hash(Rules.password(body.password())));
        }
        return UserView.of(users.save(target));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, HttpServletRequest request) {
        AppUser target = find(id);
        AppUser me = (AppUser) request.getAttribute(AuthFilter.USER_ATTR);
        if (target.getId().equals(me.getId())) throw Rules.bad("You cannot delete your own account");
        if (target.getRole() == AppUser.Role.ADMIN && target.isActive()
                && users.countByRoleAndActive(AppUser.Role.ADMIN, true) <= 1) {
            throw Rules.bad("There must always be at least one active administrator");
        }
        users.delete(target);
        return ResponseEntity.noContent().build();
    }

    private AppUser find(Long id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
>>>>>>> 9a650f05159ae8b62142f9780e931f06e227c939
