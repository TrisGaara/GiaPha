package vn.giapha.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.giapha.notification.NotificationService.NotificationDto;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationDto> mine(@RequestParam(defaultValue = "50") int limit) {
        return notificationService.mine(limit);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.unreadCount());
    }

    @PostMapping("/{id}/seen")
    public void markSeen(@PathVariable Long id) {
        notificationService.markSeen(id);
    }

    @PostMapping("/seen-all")
    public void markAllSeen() {
        notificationService.markAllSeen();
    }
}
