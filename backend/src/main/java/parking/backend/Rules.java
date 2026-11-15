package parking.backend;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Input checks shared by the sign-up, admin and password endpoints. */
public final class Rules {

    private Rules() { }

    public static String username(String s) {
        String v = s == null ? "" : s.trim();
        if (!v.matches("[A-Za-z0-9_.-]{3,30}")) {
            throw bad("Username must be 3-30 characters: letters, numbers, dot, dash or underscore");
        }
        return v;
    }

    public static String password(String s) {
        if (s == null || s.length() < 8 || s.length() > 128) {
            throw bad("Password must be 8-128 characters");
        }
        return s;
    }

    public static String fullName(String s) {
        String v = s == null ? "" : s.trim();
        if (v.length() > 80) throw bad("Name is too long (max 80 characters)");
        return v;
    }

    public static AppUser.Role role(String s) {
        try {
            return AppUser.Role.valueOf(s == null ? "" : s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw bad("Role must be ADMIN or CLIENT");
        }
    }

    public static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
