package vn.giapha.member;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.giapha.common.BusinessRuleViolationException;
import vn.giapha.family.ChildSortMode;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Kiem tra logic thu tu con cai voi "CSDL gia" trong bo nho */
class SiblingOrderServiceTest {

    private final Map<Long, FamilyMember> db = new LinkedHashMap<>();
    private SiblingOrderService service;

    private FamilyMember father;     // huyet thong
    private FamilyMember wife1;      // ngoai toc
    private FamilyMember wife2;      // ngoai toc

    @BeforeEach
    void setUp() {
        FamilyMemberRepository repo = mock(FamilyMemberRepository.class);
        when(repo.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(db.get(i.<Long>getArgument(0))));
        when(repo.findAllById(any())).thenAnswer(i -> {
            List<FamilyMember> out = new ArrayList<>();
            for (Long id : i.<Iterable<Long>>getArgument(0)) {
                if (db.containsKey(id)) out.add(db.get(id));
            }
            return out;
        });
        when(repo.findChildrenOfParent(anyLong())).thenAnswer(i -> {
            Long p = i.getArgument(0);
            return db.values().stream()
                    .filter(c -> p.equals(c.getFatherId()) || p.equals(c.getMotherId()))
                    .sorted(SiblingOrderService.BY_BIRTH_ORDER).toList();
        });
        service = new SiblingOrderService(repo);

        father = person(1L, "Cha", Gender.MALE, true, null, null, 1);
        wife1 = person(2L, "Vợ cả", Gender.FEMALE, false, null, null, 1);
        wife2 = person(3L, "Vợ hai", Gender.FEMALE, false, null, null, 1);
    }

    @Test
    void groupIsFathersWholeFamilyAcrossWives() {
        child(10L, "A", Gender.MALE, wife1, 1);
        child(11L, "B", Gender.FEMALE, wife2, 2);
        assertEquals(List.of(10L, 11L), ids(service.siblingGroup(father.getId())));
        assertTrue(service.siblingGroup(wife2.getId()).isEmpty(), "vợ ngoại tộc không phải đại diện nhóm");
    }

    @Test
    void nextBirthOrderIsMaxPlusOneEvenWithGaps() {
        child(10L, "A", Gender.MALE, wife1, 1);
        child(11L, "B", Gender.MALE, wife1, 4); // du lieu co lo hong
        assertEquals(5, service.nextBirthOrder(father.getId()));
        assertEquals(1, service.nextBirthOrder(999L));
    }

    @Test
    void placeWithoutOrderAppendsAtEnd() {
        child(10L, "A", Gender.MALE, wife1, 1);
        child(11L, "B", Gender.MALE, wife1, 2);
        FamilyMember c = child(12L, "C", Gender.FEMALE, wife2, 7);
        service.place(c, null);
        assertEquals(3, c.getBirthOrder());
    }

    @Test
    void placeAtPositionShiftsYoungerSiblings() {
        FamilyMember a = child(10L, "A", Gender.MALE, wife1, 1);
        FamilyMember b = child(11L, "B", Gender.MALE, wife1, 2);
        FamilyMember c = child(12L, "C", Gender.MALE, wife1, 3);
        FamilyMember n = child(null, "Mới", Gender.FEMALE, wife1, null); // chua luu
        service.place(n, 2);
        assertEquals(List.of(1, 2, 3, 4), List.of(a.getBirthOrder(), n.getBirthOrder(), b.getBirthOrder(), c.getBirthOrder()));
    }

    @Test
    void moveWithinGroupAndCompactAfterLeaving() {
        FamilyMember a = child(10L, "A", Gender.MALE, wife1, 1);
        FamilyMember b = child(11L, "B", Gender.MALE, wife1, 2);
        FamilyMember c = child(12L, "C", Gender.MALE, wife1, 3);
        service.place(c, 1); // C len lam con ca
        assertEquals(List.of(2, 3, 1), List.of(a.getBirthOrder(), b.getBirthOrder(), c.getBirthOrder()));

        db.remove(c.getId()); // C roi nhom
        service.compact(father.getId(), c.getId());
        assertEquals(List.of(1, 2), List.of(a.getBirthOrder(), b.getBirthOrder()));
    }

    @Test
    void reorderRequiresExactSetOfChildren() {
        FamilyMember a = child(10L, "A", Gender.MALE, wife1, 1);
        FamilyMember b = child(11L, "B", Gender.FEMALE, wife1, 2);
        FamilyMember c = child(12L, "C", Gender.MALE, wife2, 3);
        service.reorder(father.getId(), List.of(12L, 10L, 11L));
        assertEquals(List.of(2, 3, 1), List.of(a.getBirthOrder(), b.getBirthOrder(), c.getBirthOrder()));

        assertThrows(BusinessRuleViolationException.class, () -> service.reorder(father.getId(), List.of(10L, 11L)));
        assertThrows(BusinessRuleViolationException.class, () -> service.reorder(father.getId(), List.of(10L, 10L, 11L)));
        assertThrows(BusinessRuleViolationException.class, () -> service.reorder(father.getId(), List.of(10L, 11L, 99L)));
    }

    @Test
    void daughtersChildrenWithInLawHusbandGroupUnderMother() {
        FamilyMember daughter = person(20L, "Con gái", Gender.FEMALE, true, father.getId(), wife1.getId(), 1);
        FamilyMember sonInLaw = person(21L, "Con rể", Gender.MALE, false, null, null, 1);
        FamilyMember grand = person(22L, "Cháu ngoại", Gender.MALE, true, sonInLaw.getId(), daughter.getId(), 1);
        assertEquals(daughter.getId(), service.groupParentId(grand));
    }

    @Test
    void displayComparatorAndLabels() {
        FamilyMember d1 = child(10L, "Gái đầu", Gender.FEMALE, wife1, 1);
        FamilyMember s1 = child(11L, "Trai", Gender.MALE, wife1, 2);
        FamilyMember s2 = child(12L, "Út", Gender.MALE, wife2, 3);
        List<FamilyMember> group = List.of(d1, s1, s2);

        assertEquals(List.of(10L, 11L, 12L), ids(group.stream().sorted(SiblingOrderService.displayComparator(ChildSortMode.BIRTH_ORDER)).toList()));
        assertEquals(List.of(11L, 12L, 10L), ids(group.stream().sorted(SiblingOrderService.displayComparator(ChildSortMode.SONS_FIRST)).toList()));

        Map<Long, String> labels = SiblingOrderService.labelsOfGroup(group);
        assertEquals("Con cả · Trưởng nữ", labels.get(10L));
        assertEquals("Con thứ 2 · Trưởng nam", labels.get(11L));
        assertEquals("Con út", labels.get(12L));
        assertEquals("Con một", SiblingOrderService.labelsOfGroup(List.of(d1)).get(10L));
    }

    // ---------- helpers ----------

    private FamilyMember child(Long id, String name, Gender g, FamilyMember mother, Integer order) {
        return person(id, name, g, true, father.getId(), mother.getId(), order);
    }

    private FamilyMember person(Long id, String name, Gender g, boolean blood, Long fatherId, Long motherId, Integer order) {
        FamilyMember m = new FamilyMember();
        m.setId(id);
        m.setFullName(name);
        m.setGender(g);
        m.setBloodLine(blood);
        m.setFatherId(fatherId);
        m.setMotherId(motherId);
        m.setBirthOrder(order);
        m.setBirthDateSolar(LocalDate.of(1990, 1, 1));
        if (id != null) db.put(id, m);
        return m;
    }

    private static List<Long> ids(List<FamilyMember> list) {
        return list.stream().map(FamilyMember::getId).toList();
    }
}
