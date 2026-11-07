package vn.giapha.family;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.giapha.common.ApiException;
import vn.giapha.family.FamilyDtos.*;
import vn.giapha.family.PermissionService.Access;
import vn.giapha.member.FamilyMember;
import vn.giapha.member.FamilyMemberRepository;
import vn.giapha.security.CurrentUser;
import vn.giapha.user.User;
import vn.giapha.user.UserRepository;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FamilyService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FamilyRepository familyRepository;
    private final FamilyRoleRepository familyRoleRepository;
    private final FamilyMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    @Value("${app.family.auto-approve}")
    private boolean autoApprove;

    @Transactional
    public FamilyDto create(FamilyRequest req) {
        Long userId = CurrentUser.id();
        Family f = new Family();
        apply(f, req);
        f.setJoinCode(newJoinCode());
        f.setStatus(autoApprove || CurrentUser.get().isSuperAdmin() ? FamilyStatus.ACTIVE : FamilyStatus.PENDING);
        f.setCreatedBy(userId);
        familyRepository.save(f);

        FamilyRole owner = new FamilyRole();
        owner.setFamilyId(f.getId());
        owner.setUserId(userId);
        owner.setRole(FamilyRoleType.OWNER);
        familyRoleRepository.save(owner);
        return toDto(f, owner.getRole(), null);
    }

    @Transactional(readOnly = true)
    public List<FamilyDto> myFamilies() {
        List<FamilyRole> roles = familyRoleRepository.findByUserId(CurrentUser.id());
        Map<Long, Family> families = familyRepository.findAllById(roles.stream().map(FamilyRole::getFamilyId).toList())
                .stream().collect(Collectors.toMap(Family::getId, Function.identity()));
        return roles.stream()
                .filter(r -> families.containsKey(r.getFamilyId()))
                .map(r -> toDto(families.get(r.getFamilyId()), r.getRole(), r.getBranchRootMemberId()))
                .sorted(Comparator.comparing(FamilyDto::name))
                .toList();
    }

    @Transactional(readOnly = true)
    public FamilyDto get(Long familyId) {
        Access a = permissionService.access(familyId);
        return toDto(a.family(), a.superAdmin() && a.role() == null ? FamilyRoleType.OWNER : a.role(), a.branchRootId());
    }

    @Transactional
    public FamilyDto update(Long familyId, FamilyRequest req) {
        Access a = permissionService.require(familyId, FamilyRoleType.ADMIN);
        apply(a.family(), req);
        return get(familyId);
    }

    @Transactional
    public FamilyDto regenerateJoinCode(Long familyId) {
        Access a = permissionService.require(familyId, FamilyRoleType.ADMIN);
        a.family().setJoinCode(newJoinCode());
        return get(familyId);
    }

    @Transactional
    public void delete(Long familyId) {
        Access a = permissionService.require(familyId, FamilyRoleType.OWNER);
        familyRepository.delete(a.family());
    }

    @Transactional(readOnly = true)
    public FamilyPreview preview(String joinCode) {
        Family f = familyRepository.findByJoinCode(joinCode.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("dòng họ với mã này"));
        return new FamilyPreview(f.getId(), f.getName(), f.getSurname(), f.getOrigin(), f.getStatus());
    }

    // ---------- Vai tro ----------

    @Transactional(readOnly = true)
    public List<FamilyRoleDto> listRoles(Long familyId) {
        permissionService.require(familyId, FamilyRoleType.ADMIN);
        List<FamilyRole> roles = familyRoleRepository.findByFamilyId(familyId);
        Map<Long, User> users = userRepository.findAllById(roles.stream().map(FamilyRole::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, FamilyMember> members = memberRepository.findByFamilyIdOrderByGenerationAscBirthOrderAscIdAsc(familyId)
                .stream().collect(Collectors.toMap(FamilyMember::getId, Function.identity()));
        Map<Long, FamilyMember> byUser = members.values().stream().filter(m -> m.getUserId() != null)
                .collect(Collectors.toMap(FamilyMember::getUserId, Function.identity(), (x, y) -> x));
        return roles.stream().map(r -> {
            User u = users.get(r.getUserId());
            FamilyMember branch = r.getBranchRootMemberId() == null ? null : members.get(r.getBranchRootMemberId());
            FamilyMember linked = byUser.get(r.getUserId());
            return new FamilyRoleDto(r.getId(), r.getUserId(), u == null ? null : u.getUsername(),
                    u == null ? null : u.getFullName(), u == null ? null : u.getEmail(), r.getRole(),
                    r.getBranchRootMemberId(), branch == null ? null : branch.getFullName(),
                    linked == null ? null : linked.getId(), linked == null ? null : linked.getFullName(), r.getCreatedAt());
        }).sorted(Comparator.comparing(FamilyRoleDto::role)).toList();
    }

    @Transactional
    public void updateRole(Long familyId, Long roleId, UpdateRoleRequest req) {
        Access a = permissionService.require(familyId, FamilyRoleType.ADMIN);
        FamilyRole r = findRole(familyId, roleId);
        if (r.getRole() == FamilyRoleType.OWNER) {
            // Voi Toc truong chi cho phep cap nhat lien ket voi nguoi tren pha he
            if (req.role() != FamilyRoleType.OWNER) {
                throw ApiException.badRequest("Không thể thay đổi vai trò Tộc trưởng (chủ sở hữu)");
            }
            if (req.linkedMemberId() != null) {
                linkUserToMember(familyId, r.getUserId(), req.linkedMemberId());
            }
            return;
        }
        if (req.role() == FamilyRoleType.OWNER) {
            throw ApiException.badRequest("Không thể cấp vai trò Tộc trưởng");
        }
        boolean actorIsOwner = a.atLeast(FamilyRoleType.OWNER);
        if (!actorIsOwner && (r.getRole() == FamilyRoleType.ADMIN || req.role() == FamilyRoleType.ADMIN)) {
            throw ApiException.badRequest("Chỉ Tộc trưởng mới được cấp hoặc thu hồi quyền Quản trị");
        }
        if (req.role() == FamilyRoleType.BRANCH_MANAGER) {
            if (req.branchRootMemberId() == null) {
                throw ApiException.badRequest("Cần chọn thành viên gốc của nhánh cho Trưởng chi");
            }
            memberRepository.findByIdAndFamilyId(req.branchRootMemberId(), familyId)
                    .orElseThrow(() -> ApiException.notFound("thành viên gốc của nhánh"));
            r.setBranchRootMemberId(req.branchRootMemberId());
        } else {
            r.setBranchRootMemberId(null);
        }
        r.setRole(req.role());
        if (req.linkedMemberId() != null) {
            linkUserToMember(familyId, r.getUserId(), req.linkedMemberId());
        }
    }

    @Transactional
    public void removeRole(Long familyId, Long roleId) {
        Access a = permissionService.require(familyId, FamilyRoleType.ADMIN);
        FamilyRole r = findRole(familyId, roleId);
        if (r.getRole() == FamilyRoleType.OWNER) {
            throw ApiException.badRequest("Không thể xóa Tộc trưởng khỏi dòng họ");
        }
        if (r.getRole() == FamilyRoleType.ADMIN && !a.atLeast(FamilyRoleType.OWNER)) {
            throw ApiException.forbidden();
        }
        unlinkUser(familyId, r.getUserId());
        familyRoleRepository.delete(r);
    }

    @Transactional
    public void leave(Long familyId) {
        FamilyRole r = familyRoleRepository.findByFamilyIdAndUserId(familyId, CurrentUser.id())
                .orElseThrow(() -> ApiException.notFound("tư cách thành viên"));
        if (r.getRole() == FamilyRoleType.OWNER) {
            throw ApiException.badRequest("Tộc trưởng không thể rời dòng họ");
        }
        unlinkUser(familyId, r.getUserId());
        familyRoleRepository.delete(r);
    }

    /** Gan tai khoan voi mot nut tren cay pha he */
    @Transactional
    public void linkUserToMember(Long familyId, Long userId, Long memberId) {
        FamilyMember m = memberRepository.findByIdAndFamilyId(memberId, familyId)
                .orElseThrow(() -> ApiException.notFound("thành viên"));
        if (m.getUserId() != null && !m.getUserId().equals(userId)) {
            throw ApiException.badRequest("Thành viên này đã được gắn với một tài khoản khác");
        }
        unlinkUser(familyId, userId);
        m.setUserId(userId);
    }

    private void unlinkUser(Long familyId, Long userId) {
        memberRepository.findByFamilyIdAndUserId(familyId, userId).forEach(m -> m.setUserId(null));
        memberRepository.flush();
    }

    private FamilyRole findRole(Long familyId, Long roleId) {
        return familyRoleRepository.findById(roleId).filter(r -> r.getFamilyId().equals(familyId))
                .orElseThrow(() -> ApiException.notFound("vai trò"));
    }

    // ---------- Helpers ----------

    public FamilyDto toDto(Family f, FamilyRoleType myRole, Long branchRoot) {
        boolean admin = myRole != null && myRole.atLeast(FamilyRoleType.ADMIN);
        return new FamilyDto(f.getId(), f.getName(), f.getSurname(), f.getDescription(), f.getOrigin(),
                f.getAncestralHallAddress(), f.getStatus(), myRole, branchRoot, f.getChildSortMode(),
                admin ? f.getJoinCode() : null, admin ? f.getTelegramChatId() : null,
                memberRepository.countByFamilyId(f.getId()), familyRoleRepository.countByFamilyId(f.getId()),
                f.getCreatedAt());
    }

    private void apply(Family f, FamilyRequest req) {
        f.setName(req.name().trim());
        f.setSurname(req.surname().trim());
        f.setDescription(req.description());
        f.setOrigin(req.origin());
        f.setAncestralHallAddress(req.ancestralHallAddress());
        f.setTelegramChatId(StringUtils.hasText(req.telegramChatId()) ? req.telegramChatId().trim() : null);
        if (req.childSortMode() != null) {
            f.setChildSortMode(req.childSortMode());
        }
    }

    private String newJoinCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (familyRepository.existsByJoinCode(code));
        return code;
    }
}
