package parking.backend;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One sign-in attempt, successful or not, so admins can audit access. */
@Entity
@Table(name = "login_log")
public class LoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String username;
    private boolean success;
    @Column(length = 60)
    private String ipAddress;
    @Column(length = 40)
    private String note;                    // ok, wrong password, unknown user, locked out, disabled
    private Instant loggedAt;

    protected LoginLog() { }

    public LoginLog(String username, boolean success, String ipAddress, String note) {
        this.username = username == null ? "" : (username.length() > 50 ? username.substring(0, 50) : username);
        this.success = success;
        this.ipAddress = ipAddress;
        this.note = note;
        this.loggedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public boolean isSuccess() { return success; }
    public String getIpAddress() { return ipAddress; }
    public String getNote() { return note; }
    public Instant getLoggedAt() { return loggedAt; }
}
