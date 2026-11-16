package parking.backend;

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
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record CreateUser(String username, String password, String fullName, String role) { }
    public record UpdateUser(String fullName, String role, Boolean active, String password) { }
    public record LoginView(Long id, String username, boolean success, String ipAddress, String note, String loggedAt) { }
    public record SessionView(Long id, int slotNumber, String startedAt, String endedAt,
                              long durationSeconds, boolean active) { }

    private final AppUserRepository users;
    private final LoginLogRepository logins;
    private final SlotEventRepository events;
    private final ParkingSessionRepository sessions;
    private final ZoneId zone;

    public AdminController(AppUserRepository users, LoginLogRepository logins, SlotEventRepository events,
                           ParkingSessionRepository sessions, @Value("${parking.timezone:}") String timezone) {
        this.users = users;
        this.logins = logins;
        this.events = events;
        this.sessions = sessions;
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

    @GetMapping("/sessions")
    public List<SessionView> sessions(@RequestParam(defaultValue = "50") int limit,
                                      @RequestParam(required = false) Integer slot) {
        int size = Math.min(Math.max(limit, 1), 500);
        Instant now = Instant.now();
        List<ParkingSession> rows = slot == null
                ? sessions.findAllByOrderByStartedAtDesc(PageRequest.of(0, size))
                : sessions.findBySlotNumberOrderByStartedAtDesc(slot, PageRequest.of(0, size));
        return rows.stream()
                .map(s -> {
                    Instant end = s.getEndedAt() == null ? now : s.getEndedAt();
                    long duration = Math.max(0, java.time.Duration.between(s.getStartedAt(), end).getSeconds());
                    return new SessionView(s.getId(), s.getSlotNumber(), s.getStartedAt().toString(),
                            s.getEndedAt() == null ? null : s.getEndedAt().toString(), duration,
                            s.getEndedAt() == null);
                })
                .toList();
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
