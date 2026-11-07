package vn.giapha.family;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Mot dong ho / gia pha doc lap (tenant) */
@Entity
@Table(name = "families")
@Getter
@Setter
public class Family {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String surname;
    private String description;
    private String origin;
    private String ancestralHallAddress;
    private String joinCode;
    @Enumerated(EnumType.STRING)
    private FamilyStatus status = FamilyStatus.PENDING;
    private String telegramChatId;
    /** Quy uoc sap xep con cai khi hien thi */
    @Enumerated(EnumType.STRING)
    private ChildSortMode childSortMode = ChildSortMode.BIRTH_ORDER;
    private Long createdBy;
    private Instant createdAt = Instant.now();
}
