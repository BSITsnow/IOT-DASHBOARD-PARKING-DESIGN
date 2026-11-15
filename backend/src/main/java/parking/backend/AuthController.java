package parking.backend;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Admin sign in, sign out and "who am I". Every attempt is recorded in the login_log table. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(String username, String password) { }

    private static final int MAX_FAILS = 5;               // wrong passwords before a short lock-out
    private static final long LOCK_MILLIS = 60_000;
    private static final String DUMMY_HASH = PasswordHasher.hash("not-a-real-password");

    private static final class Attempt { int count; long lockedUntil; }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final AppUserRepository users;
    private final LoginLogRepository logs;

    public AuthController(AppUserRepository users, LoginLogRepository logs) {
        this.users = users;
        this.logs = logs;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest body, HttpServletRequest request) {
        String username = body.username() == null ? "" : body.username().trim();
        String password = body.password() == null ? "" : body.password();
        String ip = request.getRemoteAddr();
        String key = ip + "|" + username.toLowerCase(Locale.ROOT);

        if (isLocked(key)) {
            logs.save(new LoginLog(username, false, ip, "locked out"));
            return error(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again in a minute.");
        }

        Optional<AppUser> found = users.findByUsernameIgnoreCase(username);
        // Always run a hash check (against a dummy when the user is unknown) so timing does not reveal valid usernames.
        boolean passwordOk = PasswordHasher.verify(password, found.map(AppUser::getPasswordHash).orElse(DUMMY_HASH));
        if (found.isEmpty() || !passwordOk) {
            registerFailure(key);
            logs.save(new LoginLog(username, false, ip, found.isEmpty() ? "unknown user" : "wrong password"));
            return error(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        AppUser user = found.get();
        if (!user.isActive()) {
            logs.save(new LoginLog(username, false, ip, "disabled"));
            return error(HttpStatus.FORBIDDEN, "This account is disabled.");
        }
        if (user.getRole() != AppUser.Role.ADMIN) {
            logs.save(new LoginLog(username, false, ip, "not an admin"));
            return error(HttpStatus.FORBIDDEN, "This page is for administrators only.");
        }

        attempts.remove(key);
        user.setLastLoginAt(Instant.now());
        users.save(user);
        logs.save(new LoginLog(user.getUsername(), true, ip, "ok"));

        if (request.getSession(false) != null) request.changeSessionId();   // new session id after login (anti-fixation)
        HttpSession session = request.getSession(true);
        session.setAttribute(AuthFilter.SESSION_USER_ID, user.getId());
        return ResponseEntity.ok(UserView.of(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        AppUser user = (AppUser) request.getAttribute(AuthFilter.USER_ATTR);
        if (user == null) return error(HttpStatus.UNAUTHORIZED, "Not signed in");
        return ResponseEntity.ok(UserView.of(user));
    }

    private boolean isLocked(String key) {
        Attempt a = attempts.get(key);
        return a != null && a.lockedUntil > System.currentTimeMillis();
    }

    private void registerFailure(String key) {
        if (attempts.size() > 10_000) attempts.clear();   // keep memory bounded
        long now = System.currentTimeMillis();
        attempts.compute(key, (k, a) -> {
            if (a == null || (a.lockedUntil != 0 && a.lockedUntil <= now)) a = new Attempt();
            a.count++;
            if (a.count >= MAX_FAILS) { a.lockedUntil = now + LOCK_MILLIS; a.count = 0; }
            return a;
        });
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
