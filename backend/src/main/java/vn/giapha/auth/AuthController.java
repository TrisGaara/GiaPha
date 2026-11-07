package vn.giapha.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.giapha.auth.AuthDtos.*;
import vn.giapha.user.UserDto;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @GetMapping("/me")
    public UserDto me() {
        return authService.me();
    }

    @PutMapping("/me")
    public UserDto updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        return authService.updateProfile(req);
    }

    @PostMapping("/change-password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(req);
    }
}
