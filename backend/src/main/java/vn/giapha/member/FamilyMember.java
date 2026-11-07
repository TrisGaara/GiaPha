package vn.giapha.member;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Mot nguoi trong cay pha he (co the chua co tai khoan he thong).
 * <p>
 * Lien ket cha/me: {@code fatherId}, {@code motherId} tro nguoc lai chinh bang nay.
 * Lien ket vo/chong: xem {@link vn.giapha.marital.MaritalRelation}.
 */
@Entity
@Table(name = "family_members")
@Getter
@Setter
public class FamilyMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long familyId;
    /** Tai khoan he thong gan voi thanh vien nay (neu co) */
    private Long userId;

    private String fullName;
    /** Ten huy / ten tu / ten thuy */
    private String otherName;
    @Enumerated(EnumType.STRING)
    private Gender gender;
    /** Doi thu may (doi 1 = thuy to) */
    private Integer generation;
    /**
     * Thu tu sinh trong nhom anh chi em (1 = con ca). Luon lien tuc 1..n trong nhom,
     * duoc quan ly boi {@link SiblingOrderService}.
     */
    @Column(nullable = false)
    private Integer birthOrder = 1;
    private Long fatherId;
    private Long motherId;

    /**
     * true: thuoc huyet thong dong ho.
     * false: thanh vien ngoai toc chi co mat qua hon nhan (con dau / con re).
     */
    @Column(name = "is_blood_line")
    private boolean bloodLine = true;

    private LocalDate birthDateSolar;
    private Integer birthLunarDay;
    private Integer birthLunarMonth;
    private Integer birthLunarYear;
    private boolean birthLunarLeap;

    private boolean alive = true;
    private LocalDate deathDateSolar;
    private Integer deathLunarDay;
    private Integer deathLunarMonth;
    private Integer deathLunarYear;
    private boolean deathLunarLeap;
    private String burialPlace;

    private String hometown;
    private String occupation;
    private String biography;
    private String achievements;
    private String avatarUrl;

    // ---------- Lien he (an voi khach theo mac dinh) ----------
    private String phone;
    private String email;
    private String address;

    // ---------- Quyen rieng tu (xem vn.giapha.privacy.ViewerContext) ----------
    /** Checkbox "Cong khai chi tiet voi khach (Viewer)": false = khach chi thay ten, doi, quan he */
    @Column(name = "is_public_profile")
    private boolean publicProfile = true;
    /** false = an khoi cay pha he voi nguoi dung thong thuong (Ban quan tri van thay o dang mo) */
    @Column(name = "is_visible_on_tree")
    private boolean visibleOnTree = true;
    private boolean hideBirthDate;
    /** An ngay mat, ngay gio, noi an tang (va ngay gio tu dong trong lich su kien) */
    private boolean hideDeathDate;
    private boolean hideContactInfo = true;
    /** An tieu su, thanh tuu, nghe nghiep */
    private boolean hideBio;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    /** Thanh vien ngoai toc (dau / re) */
    public boolean isSpouseOnly() {
        return !bloodLine;
    }
}
