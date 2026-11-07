package vn.giapha.join;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "join_requests")
@Getter
@Setter
public class JoinRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long familyId;
    private Long userId;
    /** Thanh vien tren cay ma nguoi xin tham gia tu nhan la minh (tuy chon) */
    private Long claimedMemberId;
    private String message;
    /** PENDING, APPROVED, REJECTED */
    private String status = "PENDING";
    private Long reviewedBy;
    private Instant reviewedAt;
    private Instant createdAt = Instant.now();
}
