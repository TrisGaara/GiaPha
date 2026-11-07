package vn.giapha.marital;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.giapha.common.ApiException;
import vn.giapha.common.BusinessRuleViolationException;
import vn.giapha.common.DuplicateResourceException;
import vn.giapha.family.PermissionService;
import vn.giapha.member.FamilyMember;
import vn.giapha.member.FamilyMemberRepository;
import vn.giapha.member.Gender;
import vn.giapha.member.MemberService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Kiem tra cac quy tac nghiep vu hon nhan / cha me - con (khong can CSDL) */
class MaritalRulesTest {

    private MaritalRelationRepository relationRepo;
    private FamilyMemberRepository memberRepo;
    private ParentageValidator parentageValidator;
    private MaritalRelationService service;

    private final FamilyMember father = member(1L, "Nguyễn Văn Cha", Gender.MALE, true);
    private final FamilyMember wife1 = member(2L, "Trần Thị Cả", Gender.FEMALE, false);
    private final FamilyMember wife2 = member(3L, "Lê Thị Hai", Gender.FEMALE, false);
    private final FamilyMember outsiderMan = member(4L, "Phạm Văn Rể", Gender.MALE, false);
    private final FamilyMember daughter = member(5L, "Nguyễn Thị Gái", Gender.FEMALE, true);

    @BeforeEach
    void setUp() {
        relationRepo = mock(MaritalRelationRepository.class);
        memberRepo = mock(FamilyMemberRepository.class);
        parentageValidator = new ParentageValidator(relationRepo, memberRepo);
        service = new MaritalRelationService(relationRepo, memberRepo, mock(MemberService.class), mock(PermissionService.class),
                mock(vn.giapha.privacy.PrivacyService.class));
        when(memberRepo.findDescendantIds(anyLong())).thenReturn(List.of());
        when(memberRepo.findAncestorIds(anyLong())).thenReturn(List.of());
    }

    // ---------- Cha me - con ----------

    @Test
    void motherMustBeOneOfFathersWives() {
        when(relationRepo.existsByHusbandIdAndWifeId(1L, 2L)).thenReturn(true);
        when(relationRepo.existsByHusbandIdAndWifeId(1L, 3L)).thenReturn(false);

        assertDoesNotThrow(() -> parentageValidator.validate(null, father, wife1));
        assertEquals("PARENTS_NOT_MARRIED", code(() -> parentageValidator.validate(null, father, wife2)));
    }

    @Test
    void childFollowingMothersLineReportsHusbandSide() {
        ApiException e = assertThrows(BusinessRuleViolationException.class,
                () -> parentageValidator.validate(null, outsiderMan, daughter));
        assertTrue(e.getMessage().contains("đời chồng"), e.getMessage());
    }

    @Test
    void parentGendersAreChecked() {
        assertEquals("INVALID_FATHER_GENDER", code(() -> parentageValidator.validate(null, daughter, null)));
        assertEquals("INVALID_MOTHER_GENDER", code(() -> parentageValidator.validate(null, null, father)));
    }

    @Test
    void atLeastOneParentMustBeBloodLine() {
        assertEquals("PARENT_NOT_IN_BLOOD_LINE", code(() -> parentageValidator.validate(null, outsiderMan, null)));
        assertDoesNotThrow(() -> parentageValidator.validate(null, father, null));
    }

    @Test
    void cannotPickOwnDescendantAsParent() {
        FamilyMember child = member(9L, "Con", Gender.MALE, true);
        when(memberRepo.findDescendantIds(9L)).thenReturn(List.of(9L, 1L));
        assertEquals("PARENTAGE_CYCLE", code(() -> parentageValidator.validate(child, father, null)));
    }

    // ---------- Vo chong ----------

    @Test
    void duplicateMarriageIsRejected() {
        when(relationRepo.existsByHusbandIdAndWifeId(1L, 2L)).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.validateCouple(father, wife1));
    }

    @Test
    void twoOutsidersCannotMarryInsideTheTree() {
        FamilyMember outsiderWoman = member(6L, "Ngoại tộc", Gender.FEMALE, false);
        assertEquals("BOTH_SPOUSE_ONLY", code(() -> service.validateCouple(outsiderMan, outsiderWoman)));
    }

    @Test
    void sameGenderIsRejected() {
        FamilyMember brother = member(7L, "Anh", Gender.MALE, true);
        assertEquals("SAME_GENDER", code(() -> service.validateCouple(father, brother)));
    }

    @Test
    void bloodRelativesCannotMarry() {
        when(memberRepo.findDescendantIds(1L)).thenReturn(List.of(1L, 5L));
        assertEquals("CLOSE_RELATIVES", code(() -> service.validateCouple(father, daughter)));

        FamilyMember cousin = member(8L, "Họ xa", Gender.FEMALE, true);
        when(memberRepo.areSiblings(1L, 8L)).thenReturn(true);
        assertEquals("CLOSE_RELATIVES", code(() -> service.validateCouple(father, cousin)));
    }

    @Test
    void validCoupleWithOutsiderPasses() {
        assertDoesNotThrow(() -> service.validateCouple(father, wife2));
    }

    // ---------- helpers ----------

    private static String code(org.junit.jupiter.api.function.Executable ex) {
        return assertThrows(ApiException.class, ex).getCode();
    }

    private static FamilyMember member(Long id, String name, Gender g, boolean bloodLine) {
        FamilyMember m = new FamilyMember();
        m.setId(id);
        m.setFamilyId(100L);
        m.setFullName(name);
        m.setGender(g);
        m.setBloodLine(bloodLine);
        return m;
    }
}
