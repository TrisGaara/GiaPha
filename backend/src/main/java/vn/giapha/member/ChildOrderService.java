package vn.giapha.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.common.ApiException;
import vn.giapha.common.BusinessRuleViolationException;
import vn.giapha.family.FamilyRoleType;
import vn.giapha.family.PermissionService;
import vn.giapha.family.PermissionService.Access;
import vn.giapha.member.ChildOrderDtos.NextBirthOrderResponse;
import vn.giapha.member.ChildOrderDtos.SiblingDto;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tang ung dung cho tinh nang Thu tu con cai: kiem tra quyen + chuyen doi DTO,
 * logic thu tu nam o {@link SiblingOrderService}.
 */
@Service
@RequiredArgsConstructor
public class ChildOrderService {
    private final MemberService memberService;
    private final SiblingOrderService siblingOrderService;
    private final FamilyMemberRepository memberRepository;
    private final PermissionService permissionService;
    private final vn.giapha.privacy.PrivacyService privacyService;

    /**
     * Goi y thu tu cho nguoi con tiep theo cua {@code parentId} (va {@code spouseId} neu biet).
     * Nhom anh chi em duoc xac dinh nhu khi tao con that, nen gia tri goi y khop voi gia tri se duoc gan.
     */
    @Transactional(readOnly = true)
    public NextBirthOrderResponse nextBirthOrder(Long familyId, Long parentId, Long spouseId) {
        // Cong cu nhap lieu (tra ve ca anh chi em bi an) -> chi danh cho nguoi duoc them con
        permissionService.require(familyId, FamilyRoleType.BRANCH_MANAGER);
        FamilyMember parent = memberService.find(familyId, parentId);
        FamilyMember spouse = spouseId == null ? null : memberService.find(familyId, spouseId);
        if (spouse != null && spouse.getGender() == parent.getGender()) {
            throw new BusinessRuleViolationException("SAME_GENDER", "Cha và mẹ phải khác giới tính");
        }
        FamilyMember father = parent.getGender() == Gender.MALE ? parent : spouse;
        FamilyMember mother = parent.getGender() == Gender.FEMALE ? parent : spouse;
        FamilyMember groupParent = SiblingOrderService.groupParentOf(father, mother);

        List<FamilyMember> group = siblingOrderService.siblingGroup(groupParent.getId());
        Map<Long, String> labels = SiblingOrderService.labelsOfGroup(group);
        int max = SiblingOrderService.maxBirthOrder(group);
        return new NextBirthOrderResponse(groupParent.getId(), groupParent.getFullName(), group.size(), max, max + 1,
                group.stream().map(c -> SiblingDto.of(c, labels.get(c.getId()))).toList());
    }

    /**
     * Danh sach con cua mot nguoi, sap theo quy uoc hien thi cua dong ho
     * (BIRTH_ORDER: con ca -> con ut; SONS_FIRST: con trai truoc roi con gai, moi phan theo thu tu sinh).
     */
    @Transactional(readOnly = true)
    public List<SiblingDto> childrenOf(Long familyId, Long parentId) {
        Access access = permissionService.require(familyId, FamilyRoleType.VIEWER);
        vn.giapha.privacy.ViewerContext ctx = privacyService.contextFor(access);
        FamilyMember parent = memberService.find(familyId, parentId);
        privacyService.checkMemberVisibility(ctx, parent);
        List<FamilyMember> children = memberRepository.findChildrenOfParent(parent.getId()).stream()
                .filter(ctx::canSeeNode).toList(); // nguoi bi an khoi cay khong hien voi nguoi dung thong thuong
        Map<Long, String> labels = labelsOfChildren(children);
        return children.stream()
                .sorted(SiblingOrderService.displayComparator(access.family().getChildSortMode()))
                .map(c -> SiblingDto.of(c, labels.get(c.getId())))
                .toList();
    }

    /**
     * Dao lai thu tu toan bo con trong nhom cua {@code parentId}.
     * Quyen: Ban quan tri, hoac Truong chi khi nguoi cha/me thuoc nhanh minh quan ly.
     */
    @Transactional
    public List<SiblingDto> reorderChildren(Long familyId, Long parentId, List<Long> orderedChildIds) {
        Access access = permissionService.access(familyId);
        FamilyMember parent = memberService.find(familyId, parentId);
        if (!permissionService.isInBranch(access, parent.getId())) { // Admin luon true
            throw ApiException.forbidden();
        }
        List<FamilyMember> ordered = siblingOrderService.reorder(parent.getId(), orderedChildIds);
        Map<Long, String> labels = SiblingOrderService.labelsOfGroup(ordered);
        return ordered.stream().map(c -> SiblingDto.of(c, labels.get(c.getId()))).toList();
    }

    /** Danh xung cua cac con - moi nguoi lay theo nhom cua chinh minh (con cua vo hai van tinh chung nhom voi cha) */
    private Map<Long, String> labelsOfChildren(List<FamilyMember> children) {
        return children.stream()
                .map(siblingOrderService::groupParentId)
                .filter(Objects::nonNull)
                .distinct()
                .flatMap(gp -> SiblingOrderService.labelsOfGroup(siblingOrderService.siblingGroup(gp)).entrySet().stream())
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (x, y) -> x));
    }
}
