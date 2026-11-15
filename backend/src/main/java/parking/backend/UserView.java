package parking.backend;

/** What the API shows about a user. The password hash is deliberately not part of it. */
public record UserView(Long id, String username, String fullName, String role, boolean active,
                       String createdAt, String lastLoginAt) {

    public static UserView of(AppUser u) {
        return new UserView(u.getId(), u.getUsername(), u.getFullName() == null ? "" : u.getFullName(),
                u.getRole().name(), u.isActive(),
                u.getCreatedAt() == null ? null : u.getCreatedAt().toString(),
                u.getLastLoginAt() == null ? null : u.getLastLoginAt().toString());
    }
}
