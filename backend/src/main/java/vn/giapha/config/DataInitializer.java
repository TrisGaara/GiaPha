package vn.giapha.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.giapha.user.SystemRole;
import vn.giapha.user.User;
import vn.giapha.user.UserRepository;

/** Tao tai khoan Super Admin o lan khoi dong dau tien */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.super-admin.username}")
    private String username;
    @Value("${app.super-admin.password}")
    private String password;
    @Value("${app.super-admin.email}")
    private String email;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setFullName("Quản trị hệ thống");
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setSystemRole(SystemRole.SUPER_ADMIN);
        userRepository.save(u);
        log.warn("Da tao tai khoan Super Admin '{}'. Hay doi mat khau mac dinh!", username);
    }
}
