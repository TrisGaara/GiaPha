package vn.giapha.privacy;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.common.ApiException;
import vn.giapha.common.ResourceNotFoundException;
import vn.giapha.family.FamilyRoleType;
import vn.giapha.family.PermissionService;
import vn.giapha.family.PermissionService.Access;
import vn.giapha.member.FamilyMember;
import vn.giapha.member.FamilyMemberRepository;
import vn.giapha.member.MemberSpecifications;
import vn.giapha.security.UserPrincipal;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Quyen rieng tu: xac dinh ai thay gi.
 * <ul>
 *     <li>{@link #contextFor(Access)} - ngu canh nguoi xem cho mot request</li>
 *     <li>{@link #checkMemberVisibility} - kiem tra quyen xem mot thanh vien (404 neu bi an)</li>
 *     <li>{@link #treeMembers(ViewerContext)} - danh sach nut tren cay da loc + nut "an danh" giu cau truc</li>
 *     <li>{@link #applySettings} - cap nhat cau hinh rieng tu (co kiem tra quyen)</li>
 * </ul>
 * Viec che tung truong (masking) nam o {@code MemberMapper}.
 */
@Service
@RequiredArgsConstructor
public class PrivacyService {
    private final FamilyMemberRepository memberRepository;
    private final PermissionService permissionService;

    /** Ket qua dung cay cho mot nguoi xem */
    public record TreeView(List<FamilyMember> members, Set<Long> placeholderIds) {
        public boolean isPlaceholder(Long id) {
            return placeholderIds.contains(id);
        }

        public boolean contains(Long id) {
            return id != null && members.stream().anyMatch(m -> m.getId().equals(id));
        }
    }

    // =====================================================================
    // Ngu canh nguoi xem
    // =====================================================================

    public ViewerContext contextFor(Access access) {
        Set<Long> scope = permissionService.editableScope(access);
        Set<Long> self = access.userId() == null ? Set.of()
                : memberRepository.findByFamilyIdAndUserId(access.family().getId(), access.userId()).stream()
                .map(FamilyMember::getId).collect(Collectors.toSet());
        return new ViewerContext(access, scope, self);
    }

    // =====================================================================
    // Kiem tra quyen xem
    // =====================================================================

    /**
     * Kiem tra {@code currentUser} co duoc xem thanh vien {@code memberId} khong.
     *
     * @return muc do duoc xem (FULL / MEMBER / GUEST) - dung de quyet dinh che truong nao
     * @throws ResourceNotFoundException neu khong ton tai HOAC bi an voi nguoi nay (khong tiet lo su ton tai)
     * @throws ApiException              403 neu nguoi dung khong thuoc dong ho
     */
    @Transactional(readOnly = true)
    public ViewerTier checkMemberVisibility(Long memberId, UserPrincipal currentUser) {
        FamilyMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("thành viên"));
        ViewerContext ctx = contextFor(permissionService.access(member.getFamilyId(), currentUser));
        return checkMemberVisibility(ctx, member);
    }

    /** Ban dung lai ngu canh da co (tranh truy van lap lai trong cung request) */
    public ViewerTier checkMemberVisibility(ViewerContext ctx, FamilyMember member) {
        if (!member.getFamilyId().equals(ctx.familyId()) || !ctx.canSeeNode(member)) {
            throw new ResourceNotFoundException("thành viên");
        }
        return ctx.tierFor(member);
    }

    // =====================================================================
    // Cay pha he theo goc nhin nguoi xem
    // =====================================================================

    /**
     * Danh sach nut tren cay voi nguoi xem:
     * <ol>
     *     <li>Admin: tat ca (nguoi bi an duoc Frontend ve mo).</li>
     *     <li>Nguoi khac: chi nguoi dang hien + nguoi luon-hien (Specification + chi muc mot phan).</li>
     *     <li>Neu mot nguoi thuoc huyet thong bi an nhung con chau van hien -> giu lai lam nut "an danh"
     *         de cay khong bi dut nhanh. Dau/re bi an thi bo han.</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public TreeView treeMembers(ViewerContext ctx) {
        Sort sort = Sort.by("generation", "birthOrder", "id");
        if (ctx.seesEverything()) {
            return new TreeView(memberRepository.findAll(MemberSpecifications.inFamily(ctx.familyId()), sort), Set.of());
        }
        List<FamilyMember> visible = memberRepository.findAll(
                MemberSpecifications.inFamily(ctx.familyId())
                        .and(MemberSpecifications.visibleOnTreeOrIdIn(ctx.alwaysVisibleIds())), sort);

        Map<Long, FamilyMember> present = new LinkedHashMap<>();
        visible.forEach(m -> present.put(m.getId(), m));
        Set<Long> placeholders = new HashSet<>();
        Set<Long> checked = new HashSet<>(present.keySet());

        // Lan nguoc len tren: cha/me bi an cua nhung nguoi dang hien
        Set<Long> missing = missingParents(present.values(), checked);
        while (!missing.isEmpty()) {
            checked.addAll(missing);
            List<FamilyMember> hiddenParents = memberRepository.findAllById(missing).stream()
                    .filter(p -> p.getFamilyId().equals(ctx.familyId()) && p.isBloodLine())
                    .toList();
            hiddenParents.forEach(p -> {
                present.put(p.getId(), p);
                placeholders.add(p.getId());
            });
            missing = missingParents(hiddenParents, checked);
        }
        return new TreeView(new ArrayList<>(present.values()), placeholders);
    }

    private static Set<Long> missingParents(Collection<FamilyMember> members, Set<Long> checked) {
        Set<Long> ids = new HashSet<>();
        for (FamilyMember m : members) {
            if (m.getFatherId() != null && !checked.contains(m.getFatherId())) ids.add(m.getFatherId());
            if (m.getMotherId() != null && !checked.contains(m.getMotherId())) ids.add(m.getMotherId());
        }
        return ids;
    }

    // =====================================================================
    // Cap nhat cau hinh
    // =====================================================================

    /**
     * Ap dung cau hinh rieng tu (truong null = giu nguyen).
     * <ul>
     *     <li>Ban than / Truong chi / Admin: duoc doi cac co hide_* va publicProfile (da kiem tra canEdit truoc do).</li>
     *     <li>An/hien tren cay: chi Admin va Truong chi (trong nhanh).</li>
     * </ul>
     */
    public void applySettings(FamilyMember member, PrivacySettings settings, Access access) {
        if (settings == null) {
            return;
        }
        if (settings.changesTreeVisibility(member) && !access.atLeast(FamilyRoleType.BRANCH_MANAGER)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TREE_VISIBILITY_FORBIDDEN",
                    "Chỉ Ban quản trị hoặc Trưởng chi được ẩn/hiện thành viên trên cây phả hệ");
        }
        settings.copyTo(member);
    }
}
