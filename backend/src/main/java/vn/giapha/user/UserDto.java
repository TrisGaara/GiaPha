package vn.giapha.user;

import java.time.Instant;

public record UserDto(Long id, String username, String email, String fullName, String phone,
                      SystemRole systemRole, boolean enabled, Instant createdAt) {
    public static UserDto of(User u) {
        return new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getPhone(),
                u.getSystemRole(), u.isEnabled(), u.getCreatedAt());
    }
}
