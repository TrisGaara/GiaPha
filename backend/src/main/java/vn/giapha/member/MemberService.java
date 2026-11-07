package vn.giapha.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.giapha.common.ApiException;
import vn.giapha.common.BusinessRuleViolationException;
import vn.giapha.family.FamilyRoleType;
import vn.giapha.family.PermissionService;
import vn.giapha.family.PermissionService.Access;
import vn.giapha.lunar.LunarCalendar;
import vn.giapha.lunar.LunarDate;
import vn.giapha.marital.MaritalRelationRepository;
import vn.giapha.marital.ParentageValidator;
import vn.giapha.marital.dto.MaritalRelationDto;
import vn.giapha.member.MemberDtos.*;
import vn.giapha.privacy.PrivacyService;
import vn.giapha.privacy.PrivacyService.TreeView;
import vn.giapha.privacy.PrivacySettings;
import vn.giapha.privacy.ViewerContext;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final FamilyMemberRepository memberRepository;
    private final MaritalRelationRepository maritalRelationRepository;
    private final PermissionService permissionService;
    private final ParentageValidator parentageValidator;
    private final SiblingOrderService siblingOrderService;
    private final PrivacyService privacyService;

    // =====================================================================
    // Doc (luon loc + che du lieu theo nguoi xem)
    // =====================================================================

    @Transactional(readOnly = true)
    public List<MemberDto> list(Long familyId) {
        return tree(familyId).members();
    }

    /**
     * Cay pha he theo goc nhin nguoi xem:
     * Admin thay ca nguoi bi an (Frontend ve mo); nguoi khac khong thay, tru nut "an danh" giu cau truc.
     */
    @Transactional(readOnly = true)
    public TreeDto tree(Long familyId) {
        Access a = permissionService.require(familyId, FamilyRoleType.VIEWER);
        ViewerContext ctx = privacyService.contextFor(a);
        TreeView view = privacyService.treeMembers(ctx);

        Set<Long> present = view.members().stream().map(FamilyMember::getId).collect(Collectors.toSet());
        // Danh xung tinh tren ca nhom anh chi em that (thu tu sinh la du kien pha he)
        Map<Long, String> labels = SiblingOrderService.labelsForAll(
                memberRepository.findByFamilyIdOrderByGenerationAscBirthOrderAscIdAsc(familyId));
        Comparator<FamilyMember> order = Comparator
                .comparing(FamilyMember::getGeneration, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SiblingOrderService.displayComparator(a.family().getChildSortMode()));

        List<MemberDto> members = view.members().stream().sorted(order)
                .map(m -> view.isPlaceholder(m.getId())
                        ? MemberMapper.placeholder(m, present::contains)
                        : MemberMapper.toDto(m, ctx, labels.get(m.getId()), present::contains))
                .toList();
        List<MaritalRelationDto> relations = maritalRelationRepository.findByFamilyId(familyId).stream()
                .filter(r -> present.contains(r.getHusbandId()) && present.contains(r.getWifeId()))
                .map(MaritalRelationDto::of).toList();
        return new TreeDto(members, relations);
    }

    @Transactional(readOnly = true)
    public MemberDto get(Long familyId, Long id) {
        Access a = permissionService.require(familyId, FamilyRoleType.VIEWER);
        ViewerContext ctx = privacyService.contextFor(a);
        FamilyMember m = find(familyId, id);
        privacyService.checkMemberVisibility(ctx, m); // 404 neu bi an voi nguoi nay
        return MemberMapper.toDto(m, ctx, siblingOrderService.labelOf(m), refVisibility(ctx, m));
    }

    @Transactional(readOnly = true)
    public List<MemberDto> descendants(Long familyId, Long id) {
        return related(familyId, id, true);
    }

    @Transactional(readOnly = true)
    public List<MemberDto> ancestors(Long familyId, Long id) {
        return related(familyId, id, false);
    }

    private List<MemberDto> related(Long familyId, Long rootId, boolean descendants) {
        Access a = permissionService.require(familyId, FamilyRoleType.VIEWER);
        ViewerContext ctx = privacyService.contextFor(a);
        FamilyMember root = find(familyId, rootId);
        privacyService.checkMemberVisibility(ctx, root);

        List<Long> ids = descendants ? memberRepository.findDescendantIds(root.getId())
                : memberRepository.findAncestorIds(root.getId());
        List<FamilyMember> all = memberRepository.findByFamilyIdOrderByGenerationAscBirthOrderAscIdAsc(familyId);
        Map<Long, String> labels = SiblingOrderService.labelsForAll(all);
        Set<Long> visibleIds = all.stream().filter(ctx::canSeeNode).map(FamilyMember::getId).collect(Collectors.toSet());
        return memberRepository.findAllById(ids).stream()
                .filter(m -> m.getFamilyId().equals(familyId) && visibleIds.contains(m.getId()))
                .sorted(Comparator.comparingInt((FamilyMember x) -> nz(x.getGeneration())).thenComparing(SiblingOrderService.BY_BIRTH_ORDER))
                .map(m -> MemberMapper.toDto(m, ctx, labels.get(m.getId()), visibleIds::contains))
                .toList();
    }

    // =====================================================================
    // Ghi
    // =====================================================================

    @Transactional
    public MemberDto create(Long familyId, MemberRequest req) {
        Access a = permissionService.access(familyId);
        boolean allowed = a.atLeast(FamilyRoleType.ADMIN)
                || (a.isBranchManager() && (permissionService.isInBranch(a, req.fatherId())
                || permissionService.isInBranch(a, req.motherId())
                || (!req.isBloodLine() && req.fatherId() == null && req.motherId() == null)));
        if (!allowed) {
            throw ApiException.forbidden();
        }
        FamilyMember m = new FamilyMember();
        m.setFamilyId(familyId);
        applyRelations(m, req, familyId);
        applyInfo(m, req);
        privacyService.applySettings(m, req.privacy(), a);
        // Thu tu sinh: bo trong -> xep cuoi (max + 1); co nhap -> chen vao vi tri, cac anh chi em sau lui xuong
        siblingOrderService.place(m, req.birthOrder());
        FamilyMember saved = memberRepository.save(m);
        return toDtoFor(a, saved);
    }

    @Transactional
    public MemberDto update(Long familyId, Long id, MemberRequest req) {
        Access a = permissionService.access(familyId);
        FamilyMember m = find(familyId, id);
        Set<Long> scope = permissionService.editableScope(a);
        if (!permissionService.canEdit(scope, m)) {
            throw ApiException.forbidden();
        }
        // Chi Admin / Truong chi moi duoc doi quan he cha me, doi, thu bac
        if (a.atLeast(FamilyRoleType.BRANCH_MANAGER)) {
            if (a.isBranchManager() && parentsChanged(m, req) && !(permissionService.isInBranch(a, req.fatherId())
                    || permissionService.isInBranch(a, req.motherId()))) {
                throw ApiException.badRequest("Cha/mẹ mới phải thuộc nhánh bạn quản lý");
            }
            Long oldGroup = siblingOrderService.groupParentId(m);
            applyRelations(m, req, familyId);
            Long newGroup = siblingOrderService.groupParentId(m);
            if (!Objects.equals(oldGroup, newGroup)) {
                // Chuyen sang cha me khac: danh so lai nhom cu, dat vao nhom moi
                siblingOrderService.compact(oldGroup, m.getId());
                siblingOrderService.place(m, req.birthOrder());
            } else if (req.birthOrder() != null && !req.birthOrder().equals(m.getBirthOrder())) {
                // Doi thu tu trong cung nhom: di chuyen den vi tri moi
                siblingOrderService.place(m, req.birthOrder());
            }
        }
        applyInfo(m, req);
        privacyService.applySettings(m, req.privacy(), a); // ban than duoc doi hide_*, khong duoc an khoi cay
        m.setUpdatedAt(Instant.now());
        return toDtoFor(a, m);
    }

    /** Chi cap nhat cau hinh rieng tu (cac cong tac an/hien) */
    @Transactional
    public MemberDto updatePrivacy(Long familyId, Long id, PrivacySettings settings) {
        Access a = permissionService.access(familyId);
        FamilyMember m = find(familyId, id);
        if (!permissionService.canEdit(permissionService.editableScope(a), m)) {
            throw ApiException.forbidden();
        }
        privacyService.applySettings(m, settings, a);
        m.setUpdatedAt(Instant.now());
        return toDtoFor(a, m);
    }

    @Transactional
    public void delete(Long familyId, Long id) {
        permissionService.require(familyId, FamilyRoleType.ADMIN);
        FamilyMember m = find(familyId, id);
        if (memberRepository.existsByFatherIdOrMotherId(m.getId(), m.getId())) {
            throw new BusinessRuleViolationException("MEMBER_HAS_CHILDREN",
                    "Không thể xóa người đã có con trong phả hệ. Hãy xóa hoặc chuyển con cháu trước.");
        }
        Long group = siblingOrderService.groupParentId(m);
        maritalRelationRepository.deleteByHusbandIdOrWifeId(m.getId(), m.getId());
        memberRepository.delete(m);
        memberRepository.flush();
        siblingOrderService.compact(group, m.getId()); // anh chi em phia sau tien len mot bac
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    public FamilyMember find(Long familyId, Long id) {
        return memberRepository.findByIdAndFamilyId(id, familyId).orElseThrow(() -> ApiException.notFound("thành viên"));
    }

    /** DTO cho nguoi vua tao/sua (nguoi sua luon co quyen xem day du) */
    private MemberDto toDtoFor(Access a, FamilyMember m) {
        ViewerContext ctx = privacyService.contextFor(a);
        return MemberMapper.toDto(m, ctx, siblingOrderService.labelOf(m), refVisibility(ctx, m));
    }

    /** Cha/me cua m co hien voi nguoi xem khong (neu khong, id cha/me se bi xoa khoi DTO) */
    private Predicate<Long> refVisibility(ViewerContext ctx, FamilyMember m) {
        if (ctx.seesEverything()) {
            return id -> true;
        }
        Set<Long> visible = memberRepository.findAllById(
                        Arrays.asList(m.getFatherId(), m.getMotherId()).stream().filter(Objects::nonNull).toList())
                .stream().filter(ctx::canSeeNode).map(FamilyMember::getId).collect(Collectors.toSet());
        return visible::contains;
    }

    private static boolean parentsChanged(FamilyMember m, MemberRequest req) {
        return !Objects.equals(m.getFatherId(), req.fatherId()) || !Objects.equals(m.getMotherId(), req.motherId());
    }

    private void applyRelations(FamilyMember m, MemberRequest req, Long familyId) {
        FamilyMember father = req.fatherId() == null ? null : find(familyId, req.fatherId());
        FamilyMember mother = req.motherId() == null ? null : find(familyId, req.motherId());
        // Chi kiem tra khi tao moi hoac khi doi cha/me, de khong chan viec sua thong tin khac cua du lieu cu
        if (m.getId() == null || parentsChanged(m, req)) {
            parentageValidator.validate(m.getId() == null ? null : m, father, mother);
        }
        m.setFatherId(father == null ? null : father.getId());
        m.setMotherId(mother == null ? null : mother.getId());
        m.setBloodLine(req.isBloodLine());
        // birth_order duoc xu ly rieng boi SiblingOrderService (place / compact)
        Integer generation = req.generation();
        if (generation == null) {
            FamilyMember parent = father != null && father.isBloodLine() ? father : (mother != null ? mother : father);
            if (parent != null && parent.getGeneration() != null) {
                generation = parent.getGeneration() + 1;
            }
        }
        m.setGeneration(generation);
    }

    private void applyInfo(FamilyMember m, MemberRequest req) {
        m.setFullName(req.fullName().trim());
        m.setOtherName(trimToNull(req.otherName()));
        m.setGender(req.gender());

        m.setBirthDateSolar(req.birthDateSolar());
        m.setBirthLunarDay(req.birthLunarDay());
        m.setBirthLunarMonth(req.birthLunarMonth());
        m.setBirthLunarYear(req.birthLunarYear());
        m.setBirthLunarLeap(req.birthLunarLeap());
        if (req.birthDateSolar() != null) {
            LunarDate l = LunarCalendar.solarToLunar(req.birthDateSolar());
            m.setBirthLunarDay(l.day());
            m.setBirthLunarMonth(l.month());
            m.setBirthLunarYear(l.year());
            m.setBirthLunarLeap(l.leap());
        } else if (req.birthLunarDay() != null && req.birthLunarMonth() != null && req.birthLunarYear() != null) {
            m.setBirthDateSolar(LunarCalendar.lunarToSolar(req.birthLunarDay(), req.birthLunarMonth(),
                    req.birthLunarYear(), req.birthLunarLeap()));
        }

        boolean alive = req.alive() == null || req.alive();
        m.setAlive(alive);
        if (alive) {
            m.setDeathDateSolar(null);
            m.setDeathLunarDay(null);
            m.setDeathLunarMonth(null);
            m.setDeathLunarYear(null);
            m.setDeathLunarLeap(false);
            m.setBurialPlace(null);
        } else {
            m.setDeathDateSolar(req.deathDateSolar());
            m.setDeathLunarDay(req.deathLunarDay());
            m.setDeathLunarMonth(req.deathLunarMonth());
            m.setDeathLunarYear(req.deathLunarYear());
            m.setDeathLunarLeap(req.deathLunarLeap());
            m.setBurialPlace(trimToNull(req.burialPlace()));
            // Ngay mat duong lich la chuan -> tu tinh ngay gio am lich
            if (req.deathDateSolar() != null) {
                LunarDate l = LunarCalendar.solarToLunar(req.deathDateSolar());
                m.setDeathLunarDay(l.day());
                m.setDeathLunarMonth(l.month());
                m.setDeathLunarYear(l.year());
                m.setDeathLunarLeap(l.leap());
            } else if (req.deathLunarDay() != null && req.deathLunarMonth() != null && req.deathLunarYear() != null) {
                m.setDeathDateSolar(LunarCalendar.lunarToSolar(req.deathLunarDay(), req.deathLunarMonth(),
                        req.deathLunarYear(), req.deathLunarLeap()));
            }
        }
        if (m.getDeathDateSolar() != null && m.getDeathDateSolar().isAfter(LocalDate.now().plusDays(1))) {
            throw ApiException.badRequest("Ngày mất không thể ở tương lai");
        }

        m.setHometown(trimToNull(req.hometown()));
        m.setOccupation(trimToNull(req.occupation()));
        m.setBiography(trimToNull(req.biography()));
        m.setAchievements(trimToNull(req.achievements()));
        m.setAvatarUrl(trimToNull(req.avatarUrl()));
        m.setPhone(trimToNull(req.phone()));
        m.setEmail(trimToNull(req.email()));
        m.setAddress(trimToNull(req.address()));
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static int nz(Integer i) {
        return i == null ? Integer.MAX_VALUE : i;
    }
}
