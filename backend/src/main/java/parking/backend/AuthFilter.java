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
 * The gatekeeper. Every request passes through here before reaching a page or API.
 *
 *   public            login page, sign-in/sign-up calls, and the sensor/dashboard feed (/api/frame)
 *   any signed-in user  everything else (client page, change password)
 *   ADMIN only        /admin/**, /api/admin/**, /api/events
 *
 * The user is looked up in the database on every request, so disabling an account
 * or changing its role takes effect immediately, even for people already signed in.
 */
@Component
public class AuthFilter implements Filter {

    public static final String SESSION_USER_ID = "uid";
    public static final String USER_ATTR = "currentUser";

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/", "/index.html", "/login.html", "/favicon.ico", "/error",
            "/api/auth/login", "/api/auth/register", "/api/auth/logout", "/api/auth/me",
            "/api/frame");                       // the ESP32/Arduino posts here, the JavaFX app polls here

    private final AppUserRepository users;

    public AuthFilter(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // getServletPath() is already decoded and normalised (no "..", no ";x=1"), lower-cased
        // so that /ADMIN/ cannot slip past on case-insensitive file systems.
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) path = "/";
        path = path.toLowerCase(Locale.ROOT);

        AppUser user = currentUser(request);
        if (user != null) request.setAttribute(USER_ATTR, user);

        if (PUBLIC_PATHS.contains(path) || path.startsWith("/assets/")) {
            chain.doFilter(req, res);
            return;
        }

        boolean api = path.startsWith("/api/");
        if (user == null) {
            deny(response, api, HttpServletResponse.SC_UNAUTHORIZED, "Please sign in", "/login.html");
            return;
        }

        boolean adminArea = path.startsWith("/admin") || path.startsWith("/api/admin") || path.startsWith("/api/events");
        if (adminArea && user.getRole() != AppUser.Role.ADMIN) {
            deny(response, api, HttpServletResponse.SC_FORBIDDEN, "Administrators only", "/client/index.html");
            return;
        }

        chain.doFilter(req, res);
    }

    private AppUser currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);       // false = do not create a session just to look
        if (session == null) return null;
        Object id = session.getAttribute(SESSION_USER_ID);
        if (!(id instanceof Long)) return null;
        return users.findById((Long) id).filter(AppUser::isActive).orElse(null);
    }

    private static void deny(HttpServletResponse response, boolean api, int status, String message, String redirect)
            throws IOException {
        if (api) {
            response.setStatus(status);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"" + message + "\"}");
        } else {
            response.sendRedirect(redirect);
        }
    }
}
