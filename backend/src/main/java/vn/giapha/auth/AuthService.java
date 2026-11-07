package vn.giapha.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.giapha.auth.AuthDtos.*;
import vn.giapha.common.ApiException;
import vn.giapha.security.CurrentUser;
import vn.giapha.security.JwtService;
import vn.giapha.user.User;
import vn.giapha.user.UserDto;
import vn.giapha.user.UserRepository;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.username().trim())) {
            throw ApiException.badRequest("Tên đăng nhập đã tồn tại");
        }
        String email = StringUtils.hasText(req.email()) ? req.email().trim() : null;
        if (email != null && userRepository.existsByEmail(email)) {
            throw ApiException.badRequest("Email đã được sử dụng");
        }
        User u = new User();
        u.setUsername(req.username().trim());
        u.setEmail(email);
        u.setFullName(req.fullName().trim());
        u.setPhone(req.phone());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        userRepository.save(u);
        return new AuthResponse(jwtService.generate(u), UserDto.of(u));
    }

    public AuthResponse login(LoginRequest req) {
        String login = req.username().trim();
        User u = userRepository.findFirstByUsernameOrEmail(login, login)
                .filter(x -> passwordEncoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sai tên đăng nhập hoặc mật khẩu"));
        if (!u.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa");
        }
        return new AuthResponse(jwtService.generate(u), UserDto.of(u));
    }

    public UserDto me() {
        return UserDto.of(currentUser());
    }

    @Transactional
    public UserDto updateProfile(UpdateProfileRequest req) {
        User u = currentUser();
        String email = StringUtils.hasText(req.email()) ? req.email().trim() : null;
        if (email != null && !email.equalsIgnoreCase(u.getEmail()) && userRepository.existsByEmail(email)) {
            throw ApiException.badRequest("Email đã được sử dụng");
        }
        u.setEmail(email);
        u.setFullName(req.fullName().trim());
        u.setPhone(req.phone());
        return UserDto.of(u);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        User u = currentUser();
        if (!passwordEncoder.matches(req.oldPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("Mật khẩu cũ không đúng");
        }
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
    }

    private User currentUser() {
        return userRepository.findById(CurrentUser.id()).orElseThrow(() -> ApiException.notFound("người dùng"));
    }
}
