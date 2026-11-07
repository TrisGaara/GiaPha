package vn.giapha.event;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "events")
@Getter
@Setter
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long familyId;
    /** Su kien gan voi mot thanh vien (vd: mung tho ong A) */
    private Long memberId;
    private String title;
    private String description;
    @Enumerated(EnumType.STRING)
    private EventType type;
    @Enumerated(EnumType.STRING)
    private CalendarType calendarType;
    /** Dung khi calendarType = SOLAR */
    private LocalDate solarDate;
    /** Dung khi calendarType = LUNAR */
    private Integer lunarDay;
    private Integer lunarMonth;
    /** Chi can cho su kien am lich khong lap lai */
    private Integer lunarYear;
    /** Lap lai hang nam */
    private boolean recurring = true;
    private String location;
    /** Danh sach so ngay nhac truoc, vd "7,3,1" */
    private String remindDaysBefore;
    private Long createdBy;
    private Instant createdAt = Instant.now();
}
