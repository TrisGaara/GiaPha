package vn.giapha.family;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.giapha.common.ApiException;
import vn.giapha.marital.MaritalRelation;
import vn.giapha.marital.MaritalRelationRepository;
import vn.giapha.member.FamilyMember;
import vn.giapha.member.FamilyMemberRepository;
import vn.giapha.security.CurrentUser;
import vn.giapha.security.UserPrincipal;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Kiem tra quyen trong pham vi mot dong ho (tenant).
 * Moi thao tac tren du lieu dong ho deu phai di qua {@link #access(Long)} hoac {@link #require(Long, FamilyRoleType)}.
 */
@Service
@RequiredArgsConstructor
public class PermissionService {
    private final FamilyRepository familyRepository;
    private final FamilyRoleRepository familyRoleRepository;
    private final FamilyMemberRepository memberRepository;
    private final MaritalRelationRepository maritalRelationRepository;

    public record Access(Family family, FamilyRoleType role, Long branchRootId, Long userId, boolean superAdmin) {
        public boolean atLeast(FamilyRoleType min) {
            return superAdmin || (role != null && role.atLeast(min));
        }

        public boolean isBranchManager() {
            return !superAdmin && role == FamilyRoleType.BRANCH_MANAGER;
        }
    }

    public Access access(Long familyId) {
        return access(familyId, CurrentUser.get());
    }

    /** Quyen cua mot nguoi dung cu the trong dong ho (khong phu thuoc SecurityContext) */
    public Access access(Long familyId, UserPrincipal p) {
        Family family = familyRepository.findById(familyId).orElseThrow(() -> ApiException.notFound("dòng họ"));
        FamilyRole fr = familyRoleRepository.findByFamilyIdAndUserId(familyId, p.id()).orElse(null);
        if (!p.isSuperAdmin()) {
            if (fr == null) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Bạn chưa là thành viên của dòng họ này");
            }
            if (family.getStatus() == FamilyStatus.LOCKED) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Dòng họ này đang bị khóa");
            }
        }
        if (vn.giapha.security.ViewAs.isGuestPreview()) {
            return guestPreview(family);
        }
        return new Access(family, fr == null ? null : fr.getRole(), fr == null ? null : fr.getBranchRootMemberId(),
                p.id(), p.isSuperAdmin());
    }

    /**
     * "Che do nguoi xem": ha quyen xuong Khach an danh - VIEWER, khong phai Super Admin,
     * khong co nhanh quan ly, userId = null (khong duoc coi la "chinh minh" voi nut nao tren cay).
     * Chi goi SAU khi da kiem tra nguoi dung thuoc dong ho, nen khong the dung de xem dong ho khac.
     */
    public static Access guestPreview(Family family) {
        return new Access(family, FamilyRoleType.VIEWER, null, null, false);
    }

    public Access require(Long familyId, FamilyRoleType min) {
        Access a = access(familyId);
        if (!a.atLeast(min)) {
            throw ApiException.forbidden();
        }
        return a;
    }

    /**
     * Tap id thanh vien ma nguoi dung duoc phep sua. Tra ve null nghia la duoc sua tat ca (Admin tro len).
     * - Truong chi: toan bo con chau cua thanh vien goc nhanh + vo/chong cua ho.
     * - Thanh vien: ho so cua chinh minh.
     */
    public Set<Long> editableScope(Access a) {
        if (a.atLeast(FamilyRoleType.ADMIN)) {
            return null;
        }
        Set<Long> scope = new HashSet<>();
        if (a.isBranchManager() && a.branchRootId() != null) {
            List<Long> branch = memberRepository.findDescendantIds(a.branchRootId());
            scope.addAll(branch);
            for (MaritalRelation m : maritalRelationRepository.findByHusbandIdInOrWifeIdIn(branch, branch)) {
                scope.add(m.getHusbandId());
                scope.add(m.getWifeId());
            }
        }
        if (a.atLeast(FamilyRoleType.MEMBER)) {
            memberRepository.findByFamilyIdAndUserId(a.family().getId(), a.userId())
                    .forEach(m -> scope.add(m.getId()));
        }
        return scope;
    }

    public boolean canEdit(Set<Long> scope, FamilyMember m) {
        return scope == null || scope.contains(m.getId());
    }

    /** Truong chi chi duoc them con chau vao trong nhanh cua minh */
    public boolean isInBranch(Access a, Long memberId) {
        if (a.atLeast(FamilyRoleType.ADMIN)) {
            return true;
        }
        if (!a.isBranchManager() || a.branchRootId() == null || memberId == null) {
            return false;
        }
        return memberRepository.findDescendantIds(a.branchRootId()).contains(memberId);
    }
}
