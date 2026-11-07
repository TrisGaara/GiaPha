package vn.giapha.family;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.giapha.common.ApiException;
import vn.giapha.marital.MaritalRelationRepository;
import vn.giapha.member.FamilyMember;
import vn.giapha.member.FamilyMemberRepository;
import vn.giapha.member.Gender;
import vn.giapha.privacy.PrivacyService;
import vn.giapha.privacy.ViewerContext;
import vn.giapha.privacy.ViewerTier;
import vn.giapha.security.UserPrincipal;
import vn.giapha.security.ViewAs;
import vn.giapha.user.SystemRole;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** "Che do nguoi xem": header X-View-As chi HA quyen, khong bao gio nang quyen */
class ViewAsPreviewTest {

    private final FamilyRepository familyRepo = mock(FamilyRepository.class);
    private final FamilyRoleRepository roleRepo = mock(FamilyRoleRepository.class);
    private final FamilyMemberRepository memberRepo = mock(FamilyMemberRepository.class);
    private final PermissionService permissions =
            new PermissionService(familyRepo, roleRepo, memberRepo, mock(MaritalRelationRepository.class));
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private static final UserPrincipal OWNER = new UserPrincipal(1L, "owner", SystemRole.USER);
    private static final UserPrincipal OUTSIDER = new UserPrincipal(2L, "outsider", SystemRole.USER);
    private static final UserPrincipal SUPER = new UserPrincipal(3L, "super", SystemRole.SUPER_ADMIN);

    @BeforeEach
    void setUp() {
        Family family = new Family();
        family.setId(10L);
        family.setStatus(FamilyStatus.ACTIVE);
        when(familyRepo.findById(10L)).thenReturn(Optional.of(family));
        FamilyRole owner = new FamilyRole();
        owner.setRole(FamilyRoleType.OWNER);
        when(roleRepo.findByFamilyIdAndUserId(10L, 1L)).thenReturn(Optional.of(owner));
        when(roleRepo.findByFamilyIdAndUserId(10L, 2L)).thenReturn(Optional.empty());
        when(roleRepo.findByFamilyIdAndUserId(10L, 3L)).thenReturn(Optional.empty());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void withoutHeaderOwnerKeepsFullRights() {
        PermissionService.Access a = permissions.access(10L, OWNER);
        assertEquals(FamilyRoleType.OWNER, a.role());
        assertEquals(1L, a.userId());
    }

    @Test
    void headerDowngradesOwnerAndSuperAdminToAnonymousGuest() {
        request.addHeader(ViewAs.HEADER, "VIEWER");
        for (UserPrincipal p : List.of(OWNER, SUPER)) {
            PermissionService.Access a = permissions.access(10L, p);
            assertEquals(FamilyRoleType.VIEWER, a.role());
            assertFalse(a.superAdmin());
            assertNull(a.userId(), "không được coi là chính mình với nút nào trên cây");
            assertFalse(a.atLeast(FamilyRoleType.MEMBER));
        }
    }

    @Test
    void headerCannotBypassMembershipCheck() {
        request.addHeader(ViewAs.HEADER, "VIEWER");
        assertThrows(ApiException.class, () -> permissions.access(10L, OUTSIDER));
    }

    @Test
    void unknownHeaderValueIsIgnored() {
        request.addHeader(ViewAs.HEADER, "ADMIN"); // khong co gia tri nao nang quyen
        assertEquals(FamilyRoleType.OWNER, permissions.access(10L, OWNER).role());
    }

    @Test
    void previewContextMasksLikeARealGuest() {
        request.addHeader(ViewAs.HEADER, "VIEWER");
        FamilyMember self = new FamilyMember();
        self.setId(99L);
        self.setFamilyId(10L);
        self.setGender(Gender.MALE);
        self.setUserId(1L); // nut gan voi chinh tai khoan owner
        self.setHideContactInfo(true);
        when(memberRepo.findByFamilyIdAndUserId(anyLong(), anyLong())).thenReturn(List.of(self));

        ViewerContext ctx = new PrivacyService(memberRepo, permissions).contextFor(permissions.access(10L, OWNER));
        assertTrue(ctx.alwaysVisibleIds().isEmpty());
        assertEquals(ViewerTier.GUEST, ctx.tierFor(self), "ngay cả hồ sơ của chính mình cũng bị che như khách");
        assertFalse(ctx.canEdit(self));
    }
}
