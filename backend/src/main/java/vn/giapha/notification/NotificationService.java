package vn.giapha.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import vn.giapha.common.ApiException;
import vn.giapha.family.FamilyRole;
import vn.giapha.family.FamilyRoleRepository;
import vn.giapha.family.FamilyRoleType;
import vn.giapha.security.CurrentUser;
import vn.giapha.user.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Kenh thong bao: trong ung dung (luon bat), Email (MAIL_ENABLED=true), Telegram (TELEGRAM_BOT_TOKEN + chat id cua dong ho).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final FamilyRoleRepository familyRoleRepository;
    private final UserRepository userRepository;
    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${app.mail.enabled}")
    private boolean mailEnabled;
    @Value("${app.mail.from}")
    private String mailFrom;
    @Value("${app.telegram.bot-token}")
    private String telegramToken;

    public record NotificationDto(Long id, Long familyId, String title, String content, boolean seen, Instant createdAt) {
        static NotificationDto of(Notification n) {
            return new NotificationDto(n.getId(), n.getFamilyId(), n.getTitle(), n.getContent(), n.isSeen(), n.getCreatedAt());
        }
    }

    /** @return true neu thong bao moi duoc tao (khong bi trung) */
    @Transactional
    public boolean notifyUser(Long userId, Long familyId, String title, String content, String dedupeKey) {
        if (dedupeKey != null && notificationRepository.existsByUserIdAndDedupeKey(userId, dedupeKey)) {
            return false;
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setFamilyId(familyId);
        n.setTitle(title);
        n.setContent(content);
        n.setDedupeKey(dedupeKey);
        notificationRepository.save(n);
        sendEmail(userId, title, content);
        return true;
    }

    /** Gui cho moi nguoi dung trong dong ho co vai tro >= minRole */
    @Transactional
    public int notifyFamily(Long familyId, FamilyRoleType minRole, String title, String content, String dedupeKey) {
        int count = 0;
        for (FamilyRole r : familyRoleRepository.findByFamilyId(familyId)) {
            if (r.getRole().atLeast(minRole) && notifyUser(r.getUserId(), familyId, title, content, dedupeKey)) {
                count++;
            }
        }
        return count;
    }

    public void sendTelegram(String chatId, String text) {
        if (!StringUtils.hasText(telegramToken) || !StringUtils.hasText(chatId)) {
            return;
        }
        try {
            RestClient.create().post()
                    .uri("https://api.telegram.org/bot{token}/sendMessage", telegramToken)
                    .body(Map.of("chat_id", chatId, "text", text))
                    .retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("Gui Telegram that bai (chat {}): {}", chatId, e.getMessage());
        }
    }

    private void sendEmail(Long userId, String title, String content) {
        if (!mailEnabled) {
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            return;
        }
        userRepository.findById(userId).filter(u -> StringUtils.hasText(u.getEmail())).ifPresent(u -> {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(mailFrom);
                msg.setTo(u.getEmail());
                msg.setSubject("[Gia Phả] " + title);
                msg.setText(content == null ? title : content);
                sender.send(msg);
            } catch (Exception e) {
                log.warn("Gui email toi {} that bai: {}", u.getEmail(), e.getMessage());
            }
        });
    }

    // ---------- API cho nguoi dung hien tai ----------

    @Transactional(readOnly = true)
    public List<NotificationDto> mine(int limit) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(CurrentUser.id(), PageRequest.of(0, Math.min(limit, 200)))
                .stream().map(NotificationDto::of).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByUserIdAndSeenFalse(CurrentUser.id());
    }

    @Transactional
    public void markSeen(Long id) {
        Notification n = notificationRepository.findById(id).filter(x -> x.getUserId().equals(CurrentUser.id()))
                .orElseThrow(() -> ApiException.notFound("thông báo"));
        n.setSeen(true);
    }

    @Transactional
    public void markAllSeen() {
        notificationRepository.markAllSeen(CurrentUser.id());
    }
}
