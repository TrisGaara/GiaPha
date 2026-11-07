package vn.giapha.admin;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.giapha.common.ApiException;
import vn.giapha.event.EventService;
import vn.giapha.event.ReminderScheduler;
import vn.giapha.family.*;
import vn.giapha.family.FamilyDtos.FamilyDto;
import vn.giapha.notification.NotificationService;
import vn.giapha.security.CurrentUser;
import vn.giapha.user.SystemRole;
import vn.giapha.user.User;
import vn.giapha.user.UserDto;
import vn.giapha.user.UserRepository;

import java.util.List;
import java.util.Map;

/** API cho Super Admin (quan tri toan he thong) - duoc bao ve boi SecurityConfig: /api/admin/** */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final FamilyRepository familyRepository;
    private final FamilyRoleRepository familyRoleRepository;
    private final FamilyService familyService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ReminderScheduler reminderScheduler;
    private final EventService eventService;

    public record StatusRequest(@NotNull FamilyStatus status) {
    }

    public record EnabledRequest(boolean enabled) {
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return Map.of(
                "families", familyRepository.count(),
                "pendingFamilies", (long) familyRepository.findByStatus(FamilyStatus.PENDING).size(),
                "users", userRepository.count());
    }

    @GetMapping("/families")
    @Transactional(readOnly = true)
    public List<FamilyDto> families() {
        return familyRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(f -> familyService.toDto(f, FamilyRoleType.OWNER, null)).toList();
    }

    @PutMapping("/families/{id}/status")
    @Transactional
    public void setFamilyStatus(@PathVariable Long id, @RequestBody StatusRequest req) {
        Family f = familyRepository.findById(id).orElseThrow(() -> ApiException.notFound("dòng họ"));
        FamilyStatus old = f.getStatus();
        f.setStatus(req.status());
        if (old == FamilyStatus.PENDING && req.status() == FamilyStatus.ACTIVE) {
            notificationService.notifyFamily(id, FamilyRoleType.OWNER, "Dòng họ đã được duyệt",
                    "Dòng họ " + f.getName() + " đã được kích hoạt. Bạn có thể mời thành viên tham gia.", null);
        }
    }

    @GetMapping("/users")
    public List<UserDto> users() {
        return userRepository.findAll().stream().map(UserDto::of).toList();
    }

    @PutMapping("/users/{id}/enabled")
    @Transactional
    public void setUserEnabled(@PathVariable Long id, @RequestBody EnabledRequest req) {
        User u = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("người dùng"));
        if (u.getId().equals(CurrentUser.id()) || u.getSystemRole() == SystemRole.SUPER_ADMIN) {
            throw ApiException.badRequest("Không thể khóa tài khoản quản trị hệ thống");
        }
        u.setEnabled(req.enabled());
    }

    /** Chay ngay bo nhac lich (de kiem tra) */
    @PostMapping("/reminders/run")
    public Map<String, Integer> runReminders() {
        return Map.of("sent", reminderScheduler.run(eventService.today()));
    }
}
