package vn.giapha.event;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;

public final class EventDtos {
    private EventDtos() {
    }

    public record EventRequest(
            @NotBlank @Size(max = 200) String title,
            String description,
            @NotNull EventType type,
            @NotNull CalendarType calendarType,
            LocalDate solarDate,
            @Min(1) @Max(30) Integer lunarDay,
            @Min(1) @Max(12) Integer lunarMonth,
            Integer lunarYear,
            Boolean recurring,
            @Size(max = 255) String location,
            Long memberId,
            @Pattern(regexp = "^\\s*\\d{1,3}(\\s*,\\s*\\d{1,3})*\\s*$", message = "dạng 7,3,1") String remindDaysBefore) {
    }

    public record EventDto(Long id, Long familyId, Long memberId, String memberName, String title, String description,
                           EventType type, CalendarType calendarType, LocalDate solarDate,
                           Integer lunarDay, Integer lunarMonth, Integer lunarYear, boolean recurring,
                           String location, String remindDaysBefore, LocalDate nextDate, Instant createdAt) {
    }

    /** Mot lan dien ra cu the cua su kien (hoac ngay gio tu dong tu ho so nguoi da mat) */
    public record Occurrence(Long eventId, Long memberId, String title, EventType type, CalendarType calendarType,
                             LocalDate date, String lunarLabel, long daysUntil, String location, String description,
                             String remindDaysBefore, boolean auto,
                             /* Vai tro thap nhat duoc biet su kien nay (quyen rieng tu) - chi dung noi bo, khong tra ra API */
                             @com.fasterxml.jackson.annotation.JsonIgnore vn.giapha.family.FamilyRoleType audience) {
    }
}
