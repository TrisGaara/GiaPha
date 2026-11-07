package vn.giapha.marital;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Quan he hon nhan giua hai thanh vien trong cung mot dong ho.
 * <p>
 * Mot nguoi co the co nhieu ban ghi (da the lich su / tai hon). {@code marriageOrder} la thu tu
 * tinh theo <b>thanh vien huyet thong</b> cua cap do (neu ca hai deu thuoc huyet thong thi tinh theo nguoi chong):
 * 1 = Vo ca / Chinh that, 2 = Vo hai / Ke that, ...
 */
@Entity
@Table(name = "marital_relations")
@Getter
@Setter
public class MaritalRelation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long familyId;

    @Column(nullable = false)
    private Long husbandId;

    @Column(nullable = false)
    private Long wifeId;

    @Column(nullable = false)
    private int marriageOrder = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarriageStatus status = MarriageStatus.ACTIVE;

    private String note;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Id cua nguoi con lai trong cap vo chong */
    public Long partnerOf(Long memberId) {
        return husbandId.equals(memberId) ? wifeId : husbandId;
    }

    public boolean involves(Long memberId) {
        return husbandId.equals(memberId) || wifeId.equals(memberId);
    }
}
