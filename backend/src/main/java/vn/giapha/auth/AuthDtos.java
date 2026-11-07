package vn.giapha.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.giapha.user.UserDto;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "chỉ gồm chữ không dấu, số và . _ -") String username,
            @NotBlank @Size(min = 6, max = 100) String password,
            @Email String email,
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 30) String phone) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record ChangePasswordRequest(@NotBlank String oldPassword,
                                        @NotBlank @Size(min = 6, max = 100) String newPassword) {
    }

    public record UpdateProfileRequest(@Email String email, @NotBlank @Size(max = 150) String fullName,
                                       @Size(max = 30) String phone) {
    }

    public record AuthResponse(String token, UserDto user) {
    }
}
