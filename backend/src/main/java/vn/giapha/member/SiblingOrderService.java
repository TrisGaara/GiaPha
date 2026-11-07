package vn.giapha.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.giapha.common.BusinessRuleViolationException;
import vn.giapha.family.ChildSortMode;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Quan ly thu tu con cai (birth_order) - logic mien nghiep vu, KHONG kiem tra quyen.
 *
 * <h3>Nhom anh chi em (sibling group)</h3>
 * Cac con duoc danh so chung trong mot nhom, dai dien boi "nguoi cha/me nhom" (group parent):
 * <ul>
 *     <li>Cha thuoc huyet thong dong ho -> nhom theo cha (gom con voi moi doi vo: con truong la con truong cua ca nha).</li>
 *     <li>Cha la con re hoac khong ro cha -> nhom theo me (con cua con gai dong ho).</li>
 * </ul>
 * Day cung la cach cay pha he ve cac nhanh con duoi mot nguoi.
 *
 * <h3>Bat bien</h3>
 * Trong moi nhom, birth_order luon lien tuc 1..n va khong trung: moi thao tac (them, chen, doi cha me, xoa, dao thu tu)
 * deu danh so lai ca nhom.
 */
@Service
@RequiredArgsConstructor
public class SiblingOrderService {
    private final FamilyMemberRepository memberRepository;

    // =====================================================================
    // Xac dinh nhom
    // =====================================================================

    /** Nguoi dai dien nhom cua mot nguoi con khi da biet doi tuong cha (co the null) */
    public static Long groupParentId(FamilyMember child, FamilyMember father) {
        if (child.getFatherId() != null && father != null && father.isBloodLine()) {
            return child.getFatherId();
        }
        return child.getMotherId() != null ? child.getMotherId() : child.getFatherId();
    }

    /** Nguoi dai dien nhom khi biet cap cha / me (dung truoc khi tao con) */
    public static FamilyMember groupParentOf(FamilyMember father, FamilyMember mother) {
        if (father != null && father.isBloodLine()) {
            return father;
        }
        return mother != null ? mother : father;
    }

    public Long groupParentId(FamilyMember child) {
        FamilyMember father = child.getFatherId() == null ? null
                : memberRepository.findById(child.getFatherId()).orElse(null);
        return groupParentId(child, father);
    }

    /** Cac con thuoc nhom cua groupParentId, sap theo birth_order tang dan */
    public List<FamilyMember> siblingGroup(Long groupParentId) {
        if (groupParentId == null) {
            return List.of();
        }
        List<FamilyMember> children = memberRepository.findChildrenOfParent(groupParentId);
        Map<Long, FamilyMember> fathers = memberRepository.findAllById(children.stream()
                        .map(FamilyMember::getFatherId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(FamilyMember::getId, Function.identity()));
        return children.stream()
                .filter(c -> groupParentId.equals(groupParentId(c, fathers.get(c.getFatherId()))))
                .toList();
    }

    /** So thu tu tiep theo = birth_order lon nhat hien tai + 1 (nhom rong -> 1) */
    public int nextBirthOrder(Long groupParentId) {
        return maxBirthOrder(siblingGroup(groupParentId)) + 1;
    }

    public static int maxBirthOrder(List<FamilyMember> group) {
        return group.stream().map(FamilyMember::getBirthOrder).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).max().orElse(0);
    }

    // =====================================================================
    // Thay doi thu tu
    // =====================================================================

    /**
     * Dat nguoi con vao vi tri mong muon trong nhom roi danh so lai 1..n.
     * <ul>
     *     <li>{@code requestedOrder == null}: xep cuoi (= max + 1).</li>
     *     <li>Co gia tri: chen vao vi tri do, cac anh chi em tu vi tri do tro di lui xuong mot bac.</li>
     * </ul>
     * Co the goi truoc khi child duoc luu (child.id == null).
     */
    public void place(FamilyMember child, Integer requestedOrder) {
        Long groupParentId = groupParentId(child);
        if (groupParentId == null) {
            // Thuy to / nguoi khong co cha me: khong thuoc nhom nao
            child.setBirthOrder(requestedOrder == null ? 1 : requestedOrder);
            return;
        }
        List<FamilyMember> ordered = new ArrayList<>(siblingGroup(groupParentId).stream()
                .filter(s -> !s.getId().equals(child.getId()))
                .toList());
        int index = requestedOrder == null
                ? ordered.size()
                : Math.max(0, Math.min(requestedOrder - 1, ordered.size()));
        ordered.add(index, child);
        renumber(ordered);
    }

    /** Danh so lai nhom sau khi mot nguoi roi khoi nhom (bi xoa / doi cha me) */
    public void compact(Long groupParentId, Long leavingMemberId) {
        if (groupParentId == null) {
            return;
        }
        renumber(siblingGroup(groupParentId).stream()
                .filter(s -> !s.getId().equals(leavingMemberId))
                .toList());
    }

    /**
     * Dao lai thu tu ca nhom theo danh sach id (phan tu dau = con ca).
     * Danh sach phai chua dung va du cac con trong nhom.
     */
    public List<FamilyMember> reorder(Long groupParentId, List<Long> orderedChildIds) {
        List<FamilyMember> group = siblingGroup(groupParentId);
        Map<Long, FamilyMember> byId = group.stream().collect(Collectors.toMap(FamilyMember::getId, Function.identity()));
        Set<Long> requested = new LinkedHashSet<>(orderedChildIds);
        if (requested.size() != orderedChildIds.size() || !requested.equals(byId.keySet())) {
            throw new BusinessRuleViolationException("CHILD_ORDER_MISMATCH",
                    "Danh sách phải gồm đúng và đủ " + group.size() + " người con của nhóm, không trùng lặp");
        }
        List<FamilyMember> ordered = orderedChildIds.stream().map(byId::get).toList();
        renumber(ordered);
        return ordered;
    }

    /** Gan birth_order = 1..n theo dung thu tu danh sach (chi ghi nhung ban ghi thay doi) */
    private static void renumber(List<FamilyMember> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            FamilyMember m = ordered.get(i);
            if (!Objects.equals(m.getBirthOrder(), i + 1)) {
                m.setBirthOrder(i + 1);
            }
        }
    }

    // =====================================================================
    // Sap xep & danh xung khi hien thi
    // =====================================================================

    /** Thu tu sinh thuan: birth_order -> ngay sinh -> id */
    public static final Comparator<FamilyMember> BY_BIRTH_ORDER = Comparator
            .comparing(FamilyMember::getBirthOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(FamilyMember::getBirthDateSolar, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(FamilyMember::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    /** Bo so sanh dung khi tra ve cho Frontend, theo quy uoc cua dong ho */
    public static Comparator<FamilyMember> displayComparator(ChildSortMode mode) {
        if (mode == ChildSortMode.SONS_FIRST) {
            return Comparator.<FamilyMember>comparingInt(m -> m.getGender() == Gender.MALE ? 0 : 1)
                    .thenComparing(BY_BIRTH_ORDER);
        }
        return BY_BIRTH_ORDER;
    }

    /**
     * Danh xung cua tung nguoi trong mot nhom (nhom da sap theo thu tu sinh).
     * Vi du: "Con cả · Trưởng nam", "Con thứ 2 · Trưởng nữ", "Con thứ 3", "Con út", "Con một".
     */
    public static Map<Long, String> labelsOfGroup(List<FamilyMember> groupByBirth) {
        Map<Long, String> labels = new HashMap<>();
        int n = groupByBirth.size();
        boolean sonSeen = false;
        boolean daughterSeen = false;
        for (int i = 0; i < n; i++) {
            FamilyMember m = groupByBirth.get(i);
            int order = i + 1;
            if (n == 1) {
                labels.put(m.getId(), "Con một");
                continue;
            }
            String base = order == 1 ? "Con cả" : order == n ? "Con út" : "Con thứ " + order;
            String rank = null;
            if (m.getGender() == Gender.MALE && !sonSeen) {
                sonSeen = true;
                rank = "Trưởng nam";
            } else if (m.getGender() == Gender.FEMALE && !daughterSeen) {
                daughterSeen = true;
                rank = "Trưởng nữ";
            }
            labels.put(m.getId(), rank == null ? base : base + " · " + rank);
        }
        return labels;
    }

    /** Danh xung cho toan bo thanh vien mot dong ho - tinh trong bo nho, khong truy van them */
    public static Map<Long, String> labelsForAll(Collection<FamilyMember> all) {
        Map<Long, FamilyMember> byId = all.stream().collect(Collectors.toMap(FamilyMember::getId, Function.identity()));
        Map<Long, List<FamilyMember>> groups = new HashMap<>();
        for (FamilyMember c : all) {
            Long gp = groupParentId(c, byId.get(c.getFatherId()));
            if (gp != null) {
                groups.computeIfAbsent(gp, k -> new ArrayList<>()).add(c);
            }
        }
        Map<Long, String> labels = new HashMap<>();
        for (List<FamilyMember> group : groups.values()) {
            group.sort(BY_BIRTH_ORDER);
            labels.putAll(labelsOfGroup(group));
        }
        return labels;
    }

    /** Danh xung cua mot nguoi (truy van nhom cua nguoi do) */
    public String labelOf(FamilyMember m) {
        Long gp = groupParentId(m);
        return gp == null ? null : labelsOfGroup(siblingGroup(gp)).get(m.getId());
    }
}
