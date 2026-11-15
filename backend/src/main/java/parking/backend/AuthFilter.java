package parking.backend;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Every request passes through here.
 *   public      the login page, the login/logout/me calls, and /api/frame (the ESP32 posts here, the JavaFX app polls it)
 *   everything else (admin page, /api/admin/**, /api/events, H2 console) needs a signed-in, active ADMIN
 * The user is re-read from the database on each request, so disabling an account takes effect immediately.
 */
@Component
public class AuthFilter implements Filter {

    public static final String SESSION_USER_ID = "uid";
    public static final String USER_ATTR = "currentUser";

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/", "/index.html", "/login.html", "/favicon.ico", "/error",
            "/api/auth/login", "/api/auth/logout", "/api/auth/me",
            "/api/frame");

    private final AppUserRepository users;

    public AuthFilter(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // getServletPath() is decoded and normalised; lower-case it so /ADMIN/ cannot slip past on Windows.
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) path = "/";
        path = path.toLowerCase(Locale.ROOT);

        AppUser user = currentAdmin(request);
        if (user != null) request.setAttribute(USER_ATTR, user);

        if (PUBLIC_PATHS.contains(path)) {
            chain.doFilter(req, res);
            return;
        }

        if (user == null) {
            if (path.startsWith("/api/")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"error\":\"Please sign in\"}");
            } else {
                response.sendRedirect("/login.html");
            }
            return;
        }
        chain.doFilter(req, res);
    }

    private AppUser currentAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);       // false = do not create a session just to look
        if (session == null) return null;
        Object id = session.getAttribute(SESSION_USER_ID);
        if (!(id instanceof Long)) return null;
        return users.findById((Long) id)
                .filter(AppUser::isActive)
                .filter(u -> u.getRole() == AppUser.Role.ADMIN)
                .orElse(null);
    }
}
