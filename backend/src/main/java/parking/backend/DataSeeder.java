package parking.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** On first start (no admin exists yet) creates the first administrator from application.properties. */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppUserRepository users;
    private final String adminUser;
    private final String adminPassword;

    public DataSeeder(AppUserRepository users,
                      @Value("${parking.admin.username:admin}") String adminUser,
                      @Value("${parking.admin.password:admin123}") String adminPassword) {
        this.users = users;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        long admins = users.countByRoleAndActive(AppUser.Role.ADMIN, true);
        if (admins > 0 || users.existsByUsernameIgnoreCase(adminUser)) return;
        users.save(new AppUser(adminUser, PasswordHasher.hash(adminPassword), "Administrator", AppUser.Role.ADMIN));
        log.warn("Created first administrator '{}'. Sign in at /login.html and change this password.", adminUser);
    }
}
