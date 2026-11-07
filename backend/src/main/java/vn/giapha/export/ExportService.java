package vn.giapha.export;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.giapha.family.*;
import vn.giapha.family.PermissionService.Access;
import vn.giapha.lunar.LunarCalendar;
import vn.giapha.marital.MaritalRelation;
import vn.giapha.marital.MaritalRelationRepository;
import vn.giapha.marital.MarriageStatus;
import vn.giapha.marital.dto.SpouseResponseDto;
import vn.giapha.member.FamilyMember;
import vn.giapha.member.FamilyMemberRepository;
import vn.giapha.member.Gender;
import vn.giapha.member.SiblingOrderService;
import vn.giapha.privacy.MaskedField;
import vn.giapha.privacy.PrivacyService;
import vn.giapha.privacy.ViewerContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lap du lieu bao cao gia pha (dung chung cho PDF va Excel):
 * kiem tra quyen -> chon goc nhin (noi bo / cong khai) -> sap xep kieu pha ky -> tinh gia tri tung cot (co che).
 */
@Service
@RequiredArgsConstructor
public class ExportService {
    static final String HIDDEN = "(ẩn)";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int NOTE_MAX = 600;

    private final PermissionService permissionService;
    private final PrivacyService privacyService;
    private final FamilyMemberRepository memberRepository;
    private final MaritalRelationRepository maritalRelationRepository;
    private final FamilyRoleRepository familyRoleRepository;

    @Value("${app.timezone}")
    private String timezone;

    @Transactional(readOnly = true)
    public ExportReport build(Long familyId, ExportOptions options) {
        // Chi Ban quan tri / Toc truong duoc xuat toan bo pha he
        Access access = permissionService.require(familyId, FamilyRoleType.ADMIN);
        ViewerContext ctx = privacyService.contextFor(options.publicView()
                ? PermissionService.guestPreview(access.family()) // ban cong khai: che y het tai khoan Khach
                : access);

        List<FamilyMember> all = memberRepository.findByFamilyIdOrderByGenerationAscBirthOrderAscIdAsc(familyId);
        Data data = new Data(access.family(), all, maritalRelationRepository.findByFamilyId(familyId),
                accountRoles(familyId), ctx);

        List<Entry> ordered = traverse(data, options);
        int[] no = {0};
        List<ExportReport.Row> rows = ordered.stream()
                .map(e -> new ExportReport.Row(e.spouse ? null : ++no[0], e.depth, e.spouse, e.member.getGeneration(),
                        values(data, e, options.fields())))
                .toList();
        long people = ordered.stream().map(e -> e.member.getId()).distinct().count();
        return new ExportReport(access.family(), options, rows, (int) people, LocalDate.now(ZoneId.of(timezone)));
    }

    // =====================================================================
    // Thu tu: theo nhanh (pha ky) hoac theo doi
    // =====================================================================

    private record Entry(FamilyMember member, int depth, boolean spouse, FamilyMember partnerOf) {
    }

    private List<Entry> traverse(Data d, ExportOptions options) {
        List<Entry> out = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Comparator<FamilyMember> childOrder = SiblingOrderService.displayComparator(d.family.getChildSortMode());

        // Goc: nguoi huyet thong khong co cha me trong pha he
        List<FamilyMember> roots = d.all.stream()
                .filter(m -> m.isBloodLine() && m.getFatherId() == null && m.getMotherId() == null).toList();
        Deque<Object[]> stack = new ArrayDeque<>();
        for (int i = roots.size() - 1; i >= 0; i--) stack.push(new Object[]{roots.get(i), 0});
        while (!stack.isEmpty()) { // duyet sau khong de quy (pha he nhieu doi khong lo tran stack)
            Object[] top = stack.pop();
            FamilyMember m = (FamilyMember) top[0];
            int depth = (int) top[1];
            if (!visited.add(m.getId())) continue;
            if (d.ctx.canSeeNode(m)) {
                out.add(new Entry(m, depth, false, null));
            }
            for (MaritalRelation r : d.partnersOf.getOrDefault(m.getId(), List.of())) {
                FamilyMember p = d.byId.get(r.partnerOf(m.getId()));
                boolean listedHere = p != null && (!p.isBloodLine() || (p.getFatherId() == null && p.getMotherId() == null));
                if (listedHere && visited.add(p.getId()) && d.ctx.canSeeNode(p)) {
                    out.add(new Entry(p, depth, true, m));
                }
            }
            List<FamilyMember> kids = new ArrayList<>(d.childrenOf.getOrDefault(m.getId(), List.of()));
            kids.sort(childOrder);
            for (int i = kids.size() - 1; i >= 0; i--) stack.push(new Object[]{kids.get(i), depth + 1});
        }
        // Nguoi chua duoc duyet (du lieu le: dau/re chua gan hon nhan...) -> cuoi danh sach
        for (FamilyMember m : d.all) {
            if (visited.add(m.getId()) && d.ctx.canSeeNode(m)) out.add(new Entry(m, 0, false, null));
        }
        if (options.sort() == ExportOptions.Sort.GENERATION) {
            // Sap on dinh theo doi: trong cung doi van giu thu tu nhanh
            out.sort(Comparator.comparing((Entry e) -> e.member.getGeneration(), Comparator.nullsLast(Comparator.naturalOrder())));
        }
        return out;
    }

    // =====================================================================
    // Gia tri tung cot (co che theo quyen rieng tu)
    // =====================================================================

    private Map<ExportField, String> values(Data d, Entry e, Set<ExportField> fields) {
        FamilyMember m = e.member;
        Set<MaskedField> mask = d.ctx.maskedFields(m);
        Map<ExportField, String> v = new EnumMap<>(ExportField.class);
        for (ExportField f : fields) {
            v.put(f, switch (f) {
                case FULL_NAME -> m.getFullName() + (StringUtils.hasText(m.getOtherName()) ? " (" + m.getOtherName() + ")" : "");
                case GENERATION -> m.getGeneration() == null ? "" : String.valueOf(m.getGeneration());
                case GENDER -> m.getGender() == Gender.MALE ? "Nam" : "Nữ";
                case BIRTH -> mask.contains(MaskedField.BIRTH_DATE) ? HIDDEN : birth(m);
                case DEATH -> m.isAlive() ? "" : mask.contains(MaskedField.DEATH_DATE) ? "Đã mất " + HIDDEN : death(m);
                case ROLE -> role(d, e);
                case PARENTS -> parents(d, m);
                case SPOUSES -> spouses(d, m);
                case HOMETOWN -> mask.contains(MaskedField.PROFILE) || mask.contains(MaskedField.BIO) ? HIDDEN
                        : join(" · ", m.getHometown(), m.getOccupation());
                case CONTACT -> mask.contains(MaskedField.CONTACT_INFO) ? HIDDEN
                        : join("\n", m.getPhone(), m.getEmail(), m.getAddress());
                case NOTE -> mask.contains(MaskedField.BIO) ? HIDDEN : truncate(join("\n", m.getBiography(), m.getAchievements()));
            });
        }
        return v;
    }

    private static String birth(FamilyMember m) {
        if (m.getBirthDateSolar() != null) {
            String s = m.getBirthDateSolar().format(DMY);
            if (m.getBirthLunarDay() != null && m.getBirthLunarMonth() != null) {
                s += "\n(ÂL " + lunar(m.getBirthLunarDay(), m.getBirthLunarMonth(), m.getBirthLunarYear(), m.isBirthLunarLeap()) + ")";
            }
            return s;
        }
        if (m.getBirthLunarDay() != null && m.getBirthLunarMonth() != null) {
            return "ÂL " + lunar(m.getBirthLunarDay(), m.getBirthLunarMonth(), m.getBirthLunarYear(), m.isBirthLunarLeap());
        }
        return m.getBirthLunarYear() != null ? "Năm " + m.getBirthLunarYear() : "";
    }

    private static String death(FamilyMember m) {
        List<String> parts = new ArrayList<>();
        if (m.getDeathDateSolar() != null) parts.add("Mất " + m.getDeathDateSolar().format(DMY));
        else if (m.getDeathLunarYear() != null) parts.add("Mất năm " + m.getDeathLunarYear());
        else parts.add("Đã mất");
        if (m.getDeathLunarDay() != null && m.getDeathLunarMonth() != null) {
            parts.add("Giỗ " + m.getDeathLunarDay() + "/" + m.getDeathLunarMonth() + " ÂL");
        }
        if (StringUtils.hasText(m.getBurialPlace())) parts.add("Táng: " + m.getBurialPlace());
        return String.join("\n", parts);
    }

    private static String lunar(int day, int month, Integer year, boolean leap) {
        return day + "/" + month + (leap ? " nhuận" : "") + (year != null ? " " + LunarCalendar.canChiYear(year) : "");
    }

    /** Vai tro: quan he trong gia dinh + chuc danh trong dong ho (Toc truong, Truong chi...) */
    private String role(Data d, Entry e) {
        FamilyMember m = e.member;
        String relation;
        if (!m.isBloodLine()) {
            relation = d.partnersOf.getOrDefault(m.getId(), List.of()).stream()
                    .filter(r -> visibleRef(d, r.partnerOf(m.getId())) != null) // khong nhac ten nguoi bi an
                    .map(r -> SpouseResponseDto.titleOf(m.getGender(), r.getMarriageOrder())
                            + " của " + d.byId.get(r.partnerOf(m.getId())).getFullName())
                    .collect(Collectors.joining("\n"));
            if (relation.isEmpty()) relation = m.getGender() == Gender.FEMALE ? "Con dâu" : "Con rể";
        } else if (m.getFatherId() == null && m.getMotherId() == null) {
            relation = Integer.valueOf(1).equals(m.getGeneration()) ? "Thủy tổ" : "Gốc nhánh";
        } else {
            String label = d.labels.get(m.getId());
            relation = (m.getGender() == Gender.MALE ? "Con trai" : "Con gái") + (label != null ? " – " + label : "");
        }
        String title = m.getUserId() == null ? null : switch (d.accountRoles.getOrDefault(m.getUserId(), FamilyRoleType.VIEWER)) {
            case OWNER -> "Tộc trưởng";
            case ADMIN -> "Ban quản trị";
            case BRANCH_MANAGER -> "Trưởng chi";
            default -> null;
        };
        return title == null ? relation : relation + "\n" + title;
    }

    private String parents(Data d, FamilyMember m) {
        FamilyMember f = visibleRef(d, m.getFatherId());
        FamilyMember mo = visibleRef(d, m.getMotherId());
        return join("\n", f == null ? null : "Cha: " + f.getFullName(), mo == null ? null : "Mẹ: " + mo.getFullName());
    }

    private String spouses(Data d, FamilyMember m) {
        return d.partnersOf.getOrDefault(m.getId(), List.of()).stream()
                .map(r -> {
                    FamilyMember p = visibleRef(d, r.partnerOf(m.getId()));
                    if (p == null) return null;
                    String status = r.getStatus() == MarriageStatus.DIVORCED ? " (đã ly hôn)"
                            : r.getStatus() == MarriageStatus.DECEASED ? " (góa)" : "";
                    return SpouseResponseDto.titleOf(p.getGender(), r.getMarriageOrder()) + ": " + p.getFullName() + status;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }

    /** Nguoi lien quan chi duoc nhac ten neu nguoi xem duoc thay ho (ban cong khai khong lo nguoi bi an) */
    private static FamilyMember visibleRef(Data d, Long id) {
        FamilyMember p = id == null ? null : d.byId.get(id);
        return p != null && d.ctx.canSeeNode(p) ? p : null;
    }

    private static String join(String sep, String... parts) {
        return Arrays.stream(parts).filter(StringUtils::hasText).map(String::trim).collect(Collectors.joining(sep));
    }

    private static String truncate(String s) {
        return s.length() <= NOTE_MAX ? s : s.substring(0, NOTE_MAX).trim() + "…";
    }

    private Map<Long, FamilyRoleType> accountRoles(Long familyId) {
        return familyRoleRepository.findByFamilyId(familyId).stream()
                .collect(Collectors.toMap(FamilyRole::getUserId, FamilyRole::getRole, (a, b) -> a));
    }

    /** Chi muc dung trong mot lan xuat */
    private static final class Data {
        final Family family;
        final List<FamilyMember> all;
        final Map<Long, FamilyMember> byId;
        final Map<Long, List<FamilyMember>> childrenOf = new HashMap<>();
        final Map<Long, List<MaritalRelation>> partnersOf = new HashMap<>();
        final Map<Long, String> labels;
        final Map<Long, FamilyRoleType> accountRoles;
        final ViewerContext ctx;

        Data(Family family, List<FamilyMember> all, List<MaritalRelation> relations,
             Map<Long, FamilyRoleType> accountRoles, ViewerContext ctx) {
            this.family = family;
            this.all = all;
            this.byId = all.stream().collect(Collectors.toMap(FamilyMember::getId, Function.identity()));
            this.labels = SiblingOrderService.labelsForAll(all);
            this.accountRoles = accountRoles;
            this.ctx = ctx;
            for (FamilyMember c : all) {
                Long gp = SiblingOrderService.groupParentId(c, byId.get(c.getFatherId()));
                if (gp != null) childrenOf.computeIfAbsent(gp, k -> new ArrayList<>()).add(c);
            }
            for (MaritalRelation r : relations) {
                partnersOf.computeIfAbsent(r.getHusbandId(), k -> new ArrayList<>()).add(r);
                partnersOf.computeIfAbsent(r.getWifeId(), k -> new ArrayList<>()).add(r);
            }
            partnersOf.values().forEach(l -> l.sort(Comparator.comparingInt(MaritalRelation::getMarriageOrder)));
        }
    }
}
