package vn.giapha.privacy;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import vn.giapha.common.ApiException;
import vn.giapha.common.ResourceNotFoundException;
import vn.giapha.family.Family;
import vn.giapha.family.FamilyRoleType;
import vn.giapha.family.PermissionService;
import vn.giapha.family.PermissionService.Access;
import vn.giapha.member.*;
import vn.giapha.member.MemberDtos.MemberDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PrivacyTest {

    private static final Family FAMILY = family();

    // ---------- Ma tran quyen xem ----------

    @Test
    void tiersByRole() {
        FamilyMember m = member(10L, true);
        assertEquals(ViewerTier.FULL, ctx(FamilyRoleType.ADMIN, null, Set.of()).tierFor(m));
        assertEquals(ViewerTier.FULL, ctx(FamilyRoleType.BRANCH_MANAGER, Set.of(10L), Set.of()).tierFor(m), "Trưởng chi trong nhánh");
        assertEquals(ViewerTier.MEMBER, ctx(FamilyRoleType.BRANCH_MANAGER, Set.of(99L), Set.of()).tierFor(m), "Trưởng chi ngoài nhánh");
        assertEquals(ViewerTier.MEMBER, ctx(FamilyRoleType.MEMBER, Set.of(), Set.of()).tierFor(m));
        assertEquals(ViewerTier.GUEST, ctx(FamilyRoleType.VIEWER, Set.of(), Set.of()).tierFor(m));
        assertEquals(ViewerTier.FULL, ctx(FamilyRoleType.VIEWER, Set.of(), Set.of(10L)).tierFor(m), "chính mình");
    }

    @Test
    void hiddenOnTreeOnlyVisibleToFullTier() {
        FamilyMember hidden = member(10L, true);
        hidden.setVisibleOnTree(false);
        assertTrue(ctx(FamilyRoleType.ADMIN, null, Set.of()).canSeeNode(hidden));
        assertFalse(ctx(FamilyRoleType.MEMBER, Set.of(), Set.of()).canSeeNode(hidden));
        assertFalse(ctx(FamilyRoleType.VIEWER, Set.of(), Set.of()).canSeeNode(hidden));
        assertTrue(ctx(FamilyRoleType.VIEWER, Set.of(), Set.of(10L)).canSeeNode(hidden));
    }

    @Test
    void guestMaskingFollowsFlags() {
        FamilyMember m = member(10L, true);
        m.setHideBirthDate(true);
        m.setHideContactInfo(true);
        ViewerContext guest = ctx(FamilyRoleType.VIEWER, Set.of(), Set.of());
        assertEquals(Set.of(MaskedField.BIRTH_DATE, MaskedField.CONTACT_INFO), guest.maskedFields(m));
        assertTrue(ctx(FamilyRoleType.MEMBER, Set.of(), Set.of()).maskedFields(m).isEmpty(), "thành viên thấy hết");

        m.setPublicProfile(false);
        assertEquals(Set.of(MaskedField.values()), guest.maskedFields(m), "hồ sơ không công khai -> che hết");
    }

    // ---------- Che du lieu trong DTO ----------

    @Test
    void mapperNullsMaskedFieldsAndHidesSettingsFromNonEditors() {
        FamilyMember m = member(10L, true);
        m.setFatherId(1L);
        m.setHideDeathDate(true);
        m.setAlive(false);
        m.setDeathDateSolar(LocalDate.of(2000, 1, 1));
        m.setDeathLunarDay(5);
        m.setDeathLunarMonth(12);
        m.setBurialPlace("Nghĩa trang làng");
        m.setPhone("0901234567");
        m.setBiography("Tiểu sử");

        MemberDto guest = MemberMapper.toDto(m, ctx(FamilyRoleType.VIEWER, Set.of(), Set.of()), null, id -> false);
        assertNull(guest.deathDateSolar());
        assertNull(guest.deathLunar());
        assertNull(guest.burialPlace());
        assertNull(guest.phone(), "liên hệ ẩn theo mặc định");
        assertEquals("Tiểu sử", guest.biography());
        assertNull(guest.fatherId(), "cha bị ẩn -> không lộ id");
        assertNull(guest.privacy());
        assertTrue(guest.hiddenFields().containsAll(List.of(MaskedField.DEATH_DATE, MaskedField.CONTACT_INFO)));

        MemberDto admin = MemberMapper.toDto(m, ctx(FamilyRoleType.ADMIN, null, Set.of()), null, id -> true);
        assertEquals("0901234567", admin.phone());
        assertNotNull(admin.deathLunar());
        assertEquals(Boolean.TRUE, admin.privacy().hideDeathDate());
        assertTrue(admin.hiddenFields().isEmpty());
    }

    // ---------- Cay pha he ----------

    @Test
    @SuppressWarnings("unchecked")
    void hiddenBloodLineAncestorBecomesPlaceholderAndHiddenInLawIsDropped() {
        FamilyMember grandpa = member(1L, true);                 // hien
        FamilyMember father = member(2L, true);                  // AN, thuoc huyet thong
        father.setFatherId(1L);
        father.setVisibleOnTree(false);
        FamilyMember mother = member(3L, false);                 // AN, con dau
        mother.setVisibleOnTree(false);
        FamilyMember child = member(4L, true);                   // hien
        child.setFatherId(2L);
        child.setMotherId(3L);

        FamilyMemberRepository repo = mock(FamilyMemberRepository.class);
        when(repo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(grandpa, child));
        when(repo.findAllById(any())).thenReturn(List.of(father, mother));
        PrivacyService service = new PrivacyService(repo, mock(PermissionService.class));

        PrivacyService.TreeView view = service.treeMembers(ctx(FamilyRoleType.MEMBER, Set.of(), Set.of()));
        assertEquals(Set.of(1L, 2L, 4L), view.members().stream().map(FamilyMember::getId).collect(java.util.stream.Collectors.toSet()));
        assertTrue(view.isPlaceholder(2L), "cha bị ẩn nhưng con còn hiện -> nút ẩn danh");
        assertFalse(view.contains(3L), "con dâu bị ẩn -> bỏ hẳn");

        MemberDto ph = MemberMapper.placeholder(father, view::contains);
        assertEquals(MemberMapper.HIDDEN_NAME, ph.fullName());
        assertTrue(ph.placeholder());
        assertEquals(1L, ph.fatherId(), "giữ liên kết cấu trúc");
    }

    @Test
    void checkMemberVisibilityReturns404ForHiddenNode() {
        FamilyMember hidden = member(10L, true);
        hidden.setVisibleOnTree(false);
        PrivacyService service = new PrivacyService(mock(FamilyMemberRepository.class), mock(PermissionService.class));
        assertThrows(ResourceNotFoundException.class,
                () -> service.checkMemberVisibility(ctx(FamilyRoleType.MEMBER, Set.of(), Set.of()), hidden));
        assertEquals(ViewerTier.FULL, service.checkMemberVisibility(ctx(FamilyRoleType.ADMIN, null, Set.of()), hidden));
    }

    // ---------- Quyen doi cau hinh ----------

    @Test
    void onlyManagersCanChangeTreeVisibility() {
        PrivacyService service = new PrivacyService(mock(FamilyMemberRepository.class), mock(PermissionService.class));
        FamilyMember self = member(10L, true);
        Access memberAccess = access(FamilyRoleType.MEMBER);

        service.applySettings(self, new PrivacySettings(null, false, true, null, null, true), memberAccess);
        assertFalse(self.isPublicProfile());
        assertTrue(self.isHideBirthDate());
        assertTrue(self.isHideBio());

        ApiException e = assertThrows(ApiException.class,
                () -> service.applySettings(self, new PrivacySettings(false, null, null, null, null, null), memberAccess));
        assertEquals("TREE_VISIBILITY_FORBIDDEN", e.getCode());

        service.applySettings(self, new PrivacySettings(false, null, null, null, null, null), access(FamilyRoleType.ADMIN));
        assertFalse(self.isVisibleOnTree());
    }

    // ---------- helpers ----------

    private static ViewerContext ctx(FamilyRoleType role, Set<Long> scope, Set<Long> self) {
        return new ViewerContext(access(role), scope, self);
    }

    private static Access access(FamilyRoleType role) {
        return new Access(FAMILY, role, null, 500L, false);
    }

    private static Family family() {
        Family f = new Family();
        f.setId(100L);
        return f;
    }

    private static FamilyMember member(Long id, boolean bloodLine) {
        FamilyMember m = new FamilyMember();
        m.setId(id);
        m.setFamilyId(100L);
        m.setFullName("Người " + id);
        m.setGender(Gender.MALE);
        m.setBloodLine(bloodLine);
        return m;
    }
}
