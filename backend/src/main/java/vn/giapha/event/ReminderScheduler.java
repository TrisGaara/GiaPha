package vn.giapha.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.giapha.event.EventDtos.Occurrence;
import vn.giapha.family.Family;
import vn.giapha.family.FamilyRepository;
import vn.giapha.family.FamilyRoleType;
import vn.giapha.family.FamilyStatus;
import vn.giapha.notification.NotificationService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/** Moi sang quet su kien sap toi cua tung dong ho va gui nhac nho */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {
    private static final int LOOKAHEAD_DAYS = 30;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FamilyRepository familyRepository;
    private final EventService eventService;
    private final NotificationService notificationService;

    @Scheduled(cron = "${app.reminder.cron}", zone = "${app.timezone}")
    public void scheduled() {
        int sent = run(eventService.today());
        log.info("Reminder job: da gui {} thong bao", sent);
    }

    public int run(LocalDate today) {
        int total = 0;
        for (Family f : familyRepository.findByStatus(FamilyStatus.ACTIVE)) {
            try {
                total += runForFamily(f, today);
            } catch (Exception e) {
                log.error("Reminder job loi voi dong ho {}: {}", f.getId(), e.getMessage(), e);
            }
        }
        return total;
    }

    private int runForFamily(Family f, LocalDate today) {
        int sent = 0;
        for (Occurrence o : eventService.occurrencesInternal(f.getId(), today, today.plusDays(LOOKAHEAD_DAYS), true)) {
            long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, o.date());
            Set<Integer> remind = eventService.remindDays(o.remindDaysBefore());
            if (daysUntil != 0 && !remind.contains((int) daysUntil)) {
                continue;
            }
            String title = daysUntil == 0 ? "Hôm nay: " + o.title() : "Còn " + daysUntil + " ngày: " + o.title();
            String content = o.title() + " vào ngày " + o.date().format(FMT) + " (âm lịch " + o.lunarLabel() + ")"
                    + (StringUtils.hasText(o.location()) ? " tại " + o.location() : "") + ".";
            String key = "rem:" + (o.eventId() != null ? "e" + o.eventId() : "m" + o.memberId()) + ":" + o.date() + ":" + daysUntil;
            // Chi gui cho nhung vai tro duoc phep biet (quyen rieng tu cua nguoi da mat)
            int n = notificationService.notifyFamily(f.getId(), o.audience(), title, content, key);
            // Nhom Telegram co the gom ca khach -> chi dang su kien cong khai
            if (n > 0 && o.audience() == FamilyRoleType.VIEWER) {
                notificationService.sendTelegram(f.getTelegramChatId(), "[" + f.getName() + "] " + title + "\n" + content);
            }
            sent += n;
        }
        return sent;
    }
}
