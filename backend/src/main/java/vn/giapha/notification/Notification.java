package vn.giapha.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Long familyId;
    private String title;
    private String content;
    private boolean seen;
    /** Khoa chong gui trung (vi du: nhac gio cung 1 ngay chi gui 1 lan) */
    private String dedupeKey;
    private Instant createdAt = Instant.now();
}
