package vn.giapha.marital;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.common.ApiException;
import vn.giapha.common.BusinessRuleViolationException;
import vn.giapha.common.DuplicateResourceException;
import vn.giapha.family.FamilyRoleType;
import vn.giapha.family.PermissionService;
import vn.giapha.family.PermissionService.Access;
import vn.giapha.marital.dto.*;
import vn.giapha.marital.dto.AddSpouseRequest.NewSpouseInfo;
import vn.giapha.marital.dto.SpouseResponseDto.ChildSummary;
import vn.giapha.member.FamilyMember;
import vn.giapha.member.FamilyMemberRepository;
import vn.giapha.member.Gender;
import vn.giapha.member.MemberDtos.MemberDto;
import vn.giapha.member.MemberDtos.MemberRequest;
import vn.giapha.member.MemberService;
import vn.giapha.privacy.MaskedField;
import vn.giapha.privacy.PrivacyService;
import vn.giapha.privacy.ViewerContext;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Nghiep vu Hon nhan (vo/chong) va lien ket Con cai.
 *
 * <h3>Quy uoc thu tu ket hon (marriageOrder)</h3>
 * Thu tu duoc tinh theo <b>thanh vien huyet thong</b> cua cap vo chong (goi la "nguoi chinh"):
 * nguoi chong neu chong thuoc dong ho, nguoc lai la nguoi vo (truong hop con gai dong ho tai hon).
 * Moi nguoi chinh khong co hai cuoc hon nhan trung thu tu.
 */
@Service
@RequiredArgsConstructor
public class MaritalRelationService {
    private final MaritalRelationRepository maritalRelationRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final MemberService memberService;
    private final PermissionService permissionService;
    private final PrivacyService privacyService;

    // =====================================================================
    // Doc
    // =====================================================================

    /**
     * Toan bo cac doi vo/chong cua mot thanh vien, sap theo marriage_order, kem con chung.
     * Ap dung quyen rieng tu: vo/chong / con bi an khoi cay se khong xuat hien; ngay sinh/mat bi che theo cau hinh.
     */
    @Transactional(readOnly = true)
    public List<SpouseResponseDto> getSpousesOfMember(Long familyId, Long memberId) {
        ViewerContext ctx = privacyService.contextFor(permissionService.require(familyId, FamilyRoleType.VIEWER));
        FamilyMember member = memberService.find(familyId, memberId);
        privacyService.checkMemberVisibility(ctx, member);

        List<MaritalRelation> relations = maritalRelationRepository.findAllOfMember(member.getId());
        Map<Long, FamilyMember> partners = familyMemberRepository
                .findAllById(relations.stream().map(r -> r.partnerOf(member.getId())).toList())
                .stream().collect(Collectors.toMap(FamilyMember::getId, Function.identity()));

        return relations.stream()
                .filter(r -> ctx.canSeeNode(partners.get(r.partnerOf(member.getId()))))
                .map(r -> toSpouseDto(ctx, r, partners.get(r.partnerOf(member.getId()))))
                .toList();
    }

    // =====================================================================
    // Them / sua / go quan he hon nhan
    // =====================================================================

    @Transactional
    public SpouseResponseDto addSpouse(Long familyId, Long memberId, AddSpouseRequest req) {
        Access access = permissionService.access(familyId);
        FamilyMember member = memberService.find(familyId, memberId);
        requireCanManage(access, member);

        FamilyMember spouse = req.existingSpouseId() != null
                ? memberService.find(familyId, req.existingSpouseId())
                : createSpouseOnlyMember(familyId, member, req.newSpouse());

        FamilyMember husband = member.getGender() == Gender.MALE ? member : spouse;
        FamilyMember wife = member.getGender() == Gender.MALE ? spouse : member;
        validateCouple(husband, wife);

        MarriageStatus status = req.status() == null ? MarriageStatus.ACTIVE : req.status();
        validateWifeAvailability(wife, status, null);

        FamilyMember primary = primaryPartner(husband, wife);
        int order = req.marriageOrder() != null ? req.marriageOrder() : nextMarriageOrder(primary.getId());
        validateOrderUnique(primary, order, null);

        MaritalRelation relation = new MaritalRelation();
        relation.setFamilyId(familyId);
        relation.setHusbandId(husband.getId());
        relation.setWifeId(wife.getId());
        relation.setMarriageOrder(order);
        relation.setStatus(status);
        relation.setNote(req.note());
        maritalRelationRepository.save(relation);

        syncSpouseOnlyGeneration(spouse, member);
        return toSpouseDto(privacyService.contextFor(access), relation, spouse);
    }

    @Transactional
    public MaritalRelationDto updateRelation(Long familyId, Long relationId, UpdateMaritalRelationRequest req) {
        Access access = permissionService.access(familyId);
        MaritalRelation relation = findRelation(familyId, relationId);
        FamilyMember husband = memberService.find(familyId, relation.getHusbandId());
        FamilyMember wife = memberService.find(familyId, relation.getWifeId());
        requireCanManage(access, husband, wife);

        validateWifeAvailability(wife, req.status(), relation.getId());
        validateOrderUnique(primaryPartner(husband, wife), req.marriageOrder(), relation.getId());

        relation.setMarriageOrder(req.marriageOrder());
        relation.setStatus(req.status());
        relation.setNote(req.note());
        return MaritalRelationDto.of(maritalRelationRepository.saveAndFlush(relation));
    }

    /**
     * Go quan he hon nhan.
     *
     * @param removeSpouseOnlyMember true: xoa luon con dau/con re neu nguoi nay khong con lien ket nao khac
     */
    @Transactional
    public void deleteRelation(Long familyId, Long relationId, boolean removeSpouseOnlyMember) {
        Access access = permissionService.access(familyId);
        MaritalRelation relation = findRelation(familyId, relationId);
        FamilyMember husband = memberService.find(familyId, relation.getHusbandId());
        FamilyMember wife = memberService.find(familyId, relation.getWifeId());
        requireCanManage(access, husband, wife);

        if (familyMemberRepository.existsByFatherIdAndMotherId(husband.getId(), wife.getId())) {
            throw new BusinessRuleViolationException("COUPLE_HAS_CHILDREN",
                    "Không thể gỡ quan hệ vì hai người đã có con chung trong phả hệ. "
                            + "Nếu đã ly hôn, hãy chuyển trạng thái sang DIVORCED.");
        }
        maritalRelationRepository.delete(relation);
        maritalRelationRepository.flush();

        if (removeSpouseOnlyMember) {
            for (FamilyMember p : List.of(husband, wife)) {
                if (isOrphanSpouseOnly(p)) {
                    familyMemberRepository.delete(p);
                }
            }
        }
    }

    // =====================================================================
    // Them con
    // =====================================================================

    /**
     * Tao mot nguoi con va lien ket chinh xac father_id / mother_id.
     * <p>
     * Toan bo rang buoc cha/me (gioi tinh, me phai la mot doi vo cua cha, it nhat mot ben thuoc huyet thong)
     * duoc kiem tra boi {@link ParentageValidator} ben trong {@link MemberService#create}, nen moi duong tao
     * thanh vien deu tuan theo cung mot quy tac.
     * <p>
     * Thu tu sinh ({@code birthOrder}) do {@link vn.giapha.member.SiblingOrderService} xu ly trong MemberService:
     * bo trong -> max + 1 cua nhom anh chi em; co nhap -> chen vao dung vi tri, cac con phia sau lui xuong mot bac.
     */
    @Transactional
    public MemberDto createChildWithParents(Long familyId, AddChildRequest req) {
        permissionService.require(familyId, FamilyRoleType.BRANCH_MANAGER); // kiem tra pham vi nhanh chi tiet trong MemberService
        MemberRequest memberRequest = new MemberRequest(
                req.fullName(), req.otherName(), req.gender(),
                null,               // doi: tu tinh = doi cua cha/me huyet thong + 1
                req.birthOrder(),
                req.fatherId(), req.motherId(),
                true,               // con cua thanh vien huyet thong => thuoc huyet thong
                req.birthDateSolar(), req.birthLunarDay(), req.birthLunarMonth(), req.birthLunarYear(), req.birthLunarLeap(),
                req.alive(), req.deathDateSolar(), req.deathLunarDay(), req.deathLunarMonth(), req.deathLunarYear(),
                req.deathLunarLeap(), req.burialPlace(),
                req.hometown(), req.occupation(), req.biography(), req.achievements(), null,
                null, null, null,   // lien he: cap nhat sau trong ho so
                req.privacy());
        return memberService.create(familyId, memberRequest);
    }

    // =====================================================================
    // Quy tac nghiep vu
    // =====================================================================

    /** Kiem tra mot cap vo chong co hop le hay khong */
    void validateCouple(FamilyMember husband, FamilyMember wife) {
        if (husband.getId().equals(wife.getId())) {
            throw new BusinessRuleViolationException("SAME_PERSON", "Vợ và chồng phải là hai người khác nhau");
        }
        if (husband.getGender() != Gender.MALE || wife.getGender() != Gender.FEMALE) {
            throw new BusinessRuleViolationException("SAME_GENDER",
                    "\"" + husband.getFullName() + "\" và \"" + wife.getFullName() + "\" cùng giới tính");
        }
        if (maritalRelationRepository.existsByHusbandIdAndWifeId(husband.getId(), wife.getId())) {
            throw new DuplicateResourceException("MARRIAGE_ALREADY_EXISTS",
                    "Quan hệ hôn nhân giữa \"" + husband.getFullName() + "\" và \"" + wife.getFullName() + "\" đã tồn tại");
        }
        if (!husband.isBloodLine() && !wife.isBloodLine()) {
            throw new BusinessRuleViolationException("BOTH_SPOUSE_ONLY",
                    "Ít nhất một bên phải thuộc huyết thống dòng họ (không ghép hai người ngoại tộc với nhau)");
        }
        if (husband.isBloodLine() && wife.isBloodLine()) {
            validateNotCloseRelatives(husband, wife);
        }
    }

    /** Hai nguoi cung dong ho: khong duoc la to tien / con chau truc he hoac anh chi em cua nhau */
    private void validateNotCloseRelatives(FamilyMember a, FamilyMember b) {
        boolean directLine = familyMemberRepository.findAncestorIds(a.getId()).contains(b.getId())
                || familyMemberRepository.findDescendantIds(a.getId()).contains(b.getId());
        if (directLine || familyMemberRepository.areSiblings(a.getId(), b.getId())) {
            throw new BusinessRuleViolationException("CLOSE_RELATIVES",
                    "\"" + a.getFullName() + "\" và \"" + b.getFullName() + "\" là họ hàng trực hệ / anh chị em ruột");
        }
    }

    /** Mot nguoi phu nu chi co mot cuoc hon nhan ACTIVE tai mot thoi diem (tai hon phai ket thuc cuoc truoc) */
    private void validateWifeAvailability(FamilyMember wife, MarriageStatus status, Long excludeRelationId) {
        if (status == MarriageStatus.ACTIVE
                && maritalRelationRepository.wifeHasOtherActiveMarriage(wife.getId(), excludeRelationId)) {
            throw new BusinessRuleViolationException("WIFE_ALREADY_MARRIED",
                    "\"" + wife.getFullName() + "\" đang có một cuộc hôn nhân ACTIVE khác. "
                            + "Hãy chuyển cuộc hôn nhân trước sang DIVORCED hoặc DECEASED.");
        }
    }

    /** Khong cho 2 cuoc hon nhan cua cung "nguoi chinh" trung thu tu (vd: hai "Vo ca") */
    private void validateOrderUnique(FamilyMember primary, int order, Long excludeRelationId) {
        boolean taken = maritalRelationRepository.findAllOfMember(primary.getId()).stream()
                .anyMatch(r -> r.getMarriageOrder() == order && !r.getId().equals(excludeRelationId));
        if (taken) {
            Gender partnerGender = primary.getGender() == Gender.MALE ? Gender.FEMALE : Gender.MALE;
            throw new DuplicateResourceException("DUPLICATE_MARRIAGE_ORDER",
                    "\"" + primary.getFullName() + "\" đã có \"" + SpouseResponseDto.titleOf(partnerGender, order)
                            + "\" (thứ tự " + order + ")");
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Admin tro len, hoac Truong chi khi mot trong cac thanh vien thuoc nhanh minh quan ly */
    private void requireCanManage(Access access, FamilyMember... members) {
        if (access.atLeast(FamilyRoleType.ADMIN)) {
            return;
        }
        if (access.isBranchManager()) {
            Set<Long> scope = permissionService.editableScope(access);
            for (FamilyMember m : members) {
                if (permissionService.canEdit(scope, m)) {
                    return;
                }
            }
        }
        throw ApiException.forbidden();
    }

    /** Tao moi con dau / con re (ngoai toc, khong co cha me trong dong ho) */
    private FamilyMember createSpouseOnlyMember(Long familyId, FamilyMember member, NewSpouseInfo info) {
        Gender gender = member.getGender() == Gender.MALE ? Gender.FEMALE : Gender.MALE;
        MemberRequest req = new MemberRequest(
                info.fullName(), info.otherName(), gender, member.getGeneration(), null,
                null, null, false,
                info.birthDateSolar(), info.birthLunarDay(), info.birthLunarMonth(), info.birthLunarYear(), info.birthLunarLeap(),
                info.alive(), info.deathDateSolar(), info.deathLunarDay(), info.deathLunarMonth(), info.deathLunarYear(),
                info.deathLunarLeap(), info.burialPlace(),
                info.hometown(), info.occupation(), info.biography(), info.achievements(), info.avatarUrl(),
                null, null, null,
                info.privacy());
        MemberDto created = memberService.create(familyId, req);
        return memberService.find(familyId, created.id());
    }

    private static FamilyMember primaryPartner(FamilyMember husband, FamilyMember wife) {
        return husband.isBloodLine() || !wife.isBloodLine() ? husband : wife;
    }

    private int nextMarriageOrder(Long primaryId) {
        return maritalRelationRepository.findAllOfMember(primaryId).stream()
                .mapToInt(MaritalRelation::getMarriageOrder).max().orElse(0) + 1;
    }

    private void syncSpouseOnlyGeneration(FamilyMember spouse, FamilyMember member) {
        if (!spouse.isBloodLine() && spouse.getGeneration() == null && member.getGeneration() != null) {
            spouse.setGeneration(member.getGeneration());
        }
    }

    private boolean isOrphanSpouseOnly(FamilyMember m) {
        return !m.isBloodLine()
                && m.getUserId() == null
                && maritalRelationRepository.findAllOfMember(m.getId()).isEmpty()
                && !familyMemberRepository.existsByFatherIdOrMotherId(m.getId(), m.getId());
    }

    private MaritalRelation findRelation(Long familyId, Long relationId) {
        return maritalRelationRepository.findByIdAndFamilyId(relationId, familyId)
                .orElseThrow(() -> ApiException.notFound("quan hệ hôn nhân"));
    }

    private SpouseResponseDto toSpouseDto(ViewerContext ctx, MaritalRelation r, FamilyMember spouse) {
        Set<MaskedField> mask = ctx.maskedFields(spouse);
        List<ChildSummary> children = familyMemberRepository
                .findByFatherIdAndMotherIdOrderByBirthOrderAscIdAsc(r.getHusbandId(), r.getWifeId()).stream()
                .filter(ctx::canSeeNode)
                .map(c -> new ChildSummary(c.getId(), c.getFullName(), c.getGender(), c.getBirthOrder(), c.isAlive()))
                .toList();
        return new SpouseResponseDto(
                r.getId(), spouse.getId(), spouse.getFullName(), spouse.getOtherName(), spouse.getGender(),
                spouse.isBloodLine(), spouse.isAlive(),
                mask.contains(MaskedField.BIRTH_DATE) ? null : spouse.getBirthDateSolar(),
                mask.contains(MaskedField.DEATH_DATE) ? null : spouse.getDeathDateSolar(),
                mask.contains(MaskedField.PROFILE) ? null : spouse.getAvatarUrl(),
                r.getMarriageOrder(), SpouseResponseDto.titleOf(spouse.getGender(), r.getMarriageOrder()),
                r.getStatus(), r.getNote(), children);
    }
}
