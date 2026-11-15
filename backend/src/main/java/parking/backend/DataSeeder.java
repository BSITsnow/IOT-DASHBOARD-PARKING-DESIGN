package parking.backend;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Makes sure an administrator account exists.
 *  - No admin yet: creates one from application.properties.
 *  - parking.admin.reset-password=true: sets that account's password back to the configured one
 *    (use it once if you forgot the password, then set it back to false).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppUserRepository users;
    private final String adminUser;
    private final String adminPassword;
    private final boolean reset;

    public DataSeeder(AppUserRepository users,
                      @Value("${parking.admin.username:admin}") String adminUser,
                      @Value("${parking.admin.password:admin123}") String adminPassword,
                      @Value("${parking.admin.reset-password:false}") boolean reset) {
        this.users = users;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
        this.reset = reset;
    }

    @Override
    public void run(String... args) {
        Optional<AppUser> existing = users.findByUsernameIgnoreCase(adminUser);
        if (existing.isPresent()) {
            if (reset) {
                AppUser u = existing.get();
                u.setPasswordHash(PasswordHasher.hash(adminPassword));
                u.setRole(AppUser.Role.ADMIN);
                u.setActive(true);
                users.save(u);
                log.warn("Reset the password of administrator '{}'. Set parking.admin.reset-password=false again.", adminUser);
            }
            return;
        }
        if (!reset && users.countByRoleAndActive(AppUser.Role.ADMIN, true) > 0) return;
        users.save(new AppUser(adminUser, PasswordHasher.hash(adminPassword), "Administrator", AppUser.Role.ADMIN));
        log.warn("Created administrator '{}'. Sign in at /login.html and change the default password in application.properties.", adminUser);
    }
}
