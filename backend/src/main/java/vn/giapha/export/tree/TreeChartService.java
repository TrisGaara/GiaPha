package vn.giapha.export.tree;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.common.ResourceNotFoundException;
import vn.giapha.family.Family;
import vn.giapha.family.FamilyRoleType;
import vn.giapha.family.PermissionService;
import vn.giapha.family.PermissionService.Access;
import vn.giapha.marital.MaritalRelation;
import vn.giapha.marital.MaritalRelationRepository;
import vn.giapha.member.FamilyMember;
import vn.giapha.member.Gender;
import vn.giapha.member.SiblingOrderService;
import vn.giapha.privacy.PrivacyService;
import vn.giapha.privacy.PrivacyService.TreeView;
import vn.giapha.privacy.ViewerContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dung mo hinh so do cay (cac o cap vo chong + con) da loc theo quyen rieng tu, roi bo cuc bang {@link TreeChartLayout}.
 * Dung chung quy tac voi cay tren web: PrivacyService.treeMembers (nut "an danh"), SiblingOrderService (thu tu con).
 */
@Service
@RequiredArgsConstructor
public class TreeChartService {
    private final PermissionService permissionService;
    private final PrivacyService privacyService;
    private final MaritalRelationRepository maritalRelationRepository;

    @Value("${app.timezone}")
    private String timezone;

    /** So do da bo cuc, san sang de ve */
    public record Chart(Family family, TreeChartOptions options, List<ChartNode> roots, TreeChartLayout.Bounds bounds,
                        ViewerContext viewer, int people, LocalDate generatedOn) {
    }

    @Transactional(readOnly = true)
    public Chart build(Long familyId, TreeChartOptions options) {
        Access access = permissionService.require(familyId, FamilyRoleType.ADMIN);
        ViewerContext ctx = privacyService.contextFor(options.publicView()
                ? PermissionService.guestPreview(access.family())
                : access);
        TreeView view = privacyService.treeMembers(ctx);
        Map<Long, FamilyMember> byId = view.members().stream().collect(Collectors.toMap(FamilyMember::getId, Function.identity()));

        // Vo/chong: chi giu quan he ma ca hai nguoi deu duoc ve
        Map<Long, List<MaritalRelation>> partnersOf = new HashMap<>();
        for (MaritalRelation r : maritalRelationRepository.findByFamilyId(familyId)) {
            if (byId.containsKey(r.getHusbandId()) && byId.containsKey(r.getWifeId())) {
                partnersOf.computeIfAbsent(r.getHusbandId(), k -> new ArrayList<>()).add(r);
                partnersOf.computeIfAbsent(r.getWifeId(), k -> new ArrayList<>()).add(r);
            }
        }
        partnersOf.values().forEach(l -> l.sort(Comparator.comparingInt(MaritalRelation::getMarriageOrder)));

        // Con theo "nguoi cha/me nhom" (giong web), sap theo quy uoc cua dong ho
        Map<Long, List<FamilyMember>> childrenOf = new HashMap<>();
        for (FamilyMember c : view.members()) {
            Long gp = SiblingOrderService.groupParentId(c, byId.get(c.getFatherId()));
            if (gp != null && byId.containsKey(gp)) childrenOf.computeIfAbsent(gp, k -> new ArrayList<>()).add(c);
        }
        Comparator<FamilyMember> order = SiblingOrderService.displayComparator(access.family().getChildSortMode());
        childrenOf.values().forEach(l -> l.sort(order));

        Builder b = new Builder(byId, view.placeholderIds(), partnersOf, childrenOf, options.maxDepth());
        List<ChartNode> roots = new ArrayList<>();
        if (options.rootId() != null) {
            FamilyMember root = byId.get(options.rootId());
            if (root == null || view.isPlaceholder(root.getId())) throw new ResourceNotFoundException("thành viên gốc");
            roots.add(b.node(root, 1));
        } else {
            view.members().stream()
                    .filter(m -> m.isBloodLine() && !attachedAsChild(m, byId))
                    .sorted(Comparator.comparing(FamilyMember::getGeneration, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(order))
                    .forEach(m -> {
                        ChartNode n = b.node(m, 1);
                        if (n != null) roots.add(n);
                    });
        }
        TreeChartLayout.Bounds bounds = TreeChartLayout.layout(roots);
        return new Chart(access.family(), options, roots, bounds, ctx, b.people, LocalDate.now(ZoneId.of(timezone)));
    }

    private static boolean attachedAsChild(FamilyMember m, Map<Long, FamilyMember> byId) {
        Long gp = SiblingOrderService.groupParentId(m, byId.get(m.getFatherId()));
        return gp != null && byId.containsKey(gp);
    }

    /** Dung cay nut (khong de quy qua sau nho gioi han doi; danh dau da tham de chong vong lap) */
    private static final class Builder {
        final Map<Long, FamilyMember> byId;
        final Set<Long> placeholders;
        final Map<Long, List<MaritalRelation>> partnersOf;
        final Map<Long, List<FamilyMember>> childrenOf;
        final int maxDepth;
        final Set<Long> visited = new HashSet<>();
        int people;

        Builder(Map<Long, FamilyMember> byId, Set<Long> placeholders, Map<Long, List<MaritalRelation>> partnersOf,
                Map<Long, List<FamilyMember>> childrenOf, int maxDepth) {
            this.byId = byId;
            this.placeholders = placeholders;
            this.partnersOf = partnersOf;
            this.childrenOf = childrenOf;
            this.maxDepth = maxDepth;
        }

        ChartNode node(FamilyMember m, int depth) {
            if (!visited.add(m.getId())) return null;
            people++;
            ChartNode n = new ChartNode(new ChartNode.Person(m, null, placeholders.contains(m.getId())));
            for (MaritalRelation r : partnersOf.getOrDefault(m.getId(), List.of())) {
                FamilyMember p = byId.get(r.partnerOf(m.getId()));
                // Vo/chong ve trong o neu la dau/re hoac khong co cha me tren cay (giong web)
                boolean inCard = !p.isBloodLine() || (p.getFatherId() == null && p.getMotherId() == null);
                if (inCard && visited.add(p.getId())) {
                    people++;
                    n.partners.add(new ChartNode.Person(p, r, placeholders.contains(p.getId())));
                }
            }
            List<FamilyMember> kids = childrenOf.getOrDefault(m.getId(), List.of());
            if (depth >= maxDepth) {
                n.hiddenDescendants = countDescendants(m.getId(), new HashSet<>());
                return n;
            }
            for (FamilyMember c : kids) {
                ChartNode child = node(c, depth + 1);
                if (child == null) continue;
                if (n.partners.size() > 1) {
                    Long other = m.getGender() == Gender.MALE ? c.getMotherId() : c.getFatherId();
                    n.partners.stream().filter(p -> p.member().getId().equals(other)).findFirst()
                            .ifPresent(p -> child.parentOrder = p.relation().getMarriageOrder());
                }
                n.children.add(child);
            }
            return n;
        }

        int countDescendants(Long id, Set<Long> seen) {
            int count = 0;
            for (FamilyMember c : childrenOf.getOrDefault(id, List.of())) {
                if (seen.add(c.getId())) count += 1 + countDescendants(c.getId(), seen);
            }
            return count;
        }
    }
}
