package vn.giapha.family;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "family_roles")
@Getter
@Setter
public class FamilyRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long familyId;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private FamilyRoleType role;
    /** Voi BRANCH_MANAGER: thanh vien goc cua nhanh duoc quan ly */
    private Long branchRootMemberId;
    private Instant createdAt = Instant.now();
}
