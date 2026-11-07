package vn.giapha.join;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.common.ApiException;
import vn.giapha.family.*;
import vn.giapha.member.FamilyMember;
import vn.giapha.member.FamilyMemberRepository;
import vn.giapha.notification.NotificationService;
import vn.giapha.security.CurrentUser;
import vn.giapha.user.User;
import vn.giapha.user.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JoinService {
    private final JoinRequestRepository joinRequestRepository;
    private final FamilyRepository familyRepository;
    private final FamilyRoleRepository familyRoleRepository;
    private final FamilyMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final FamilyService familyService;
    private final NotificationService notificationService;

    public record CreateJoinRequest(@NotBlank String joinCode, @Size(max = 500) String message, Long claimedMemberId) {
    }

    public record ApproveRequest(@NotNull FamilyRoleType role, Long memberId) {
    }

    public record JoinRequestDto(Long id, Long familyId, String familyName, Long userId, String username,
                                 String fullName, String email, String phone, Long claimedMemberId,
                                 String claimedMemberName, String message, String status, Instant createdAt) {
    }

    @Transactional
    public JoinRequestDto create(CreateJoinRequest req) {
        Long userId = CurrentUser.id();
        Family f = familyRepository.findByJoinCode(req.joinCode().trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("dòng họ với mã này"));
        if (f.getStatus() == FamilyStatus.LOCKED) {
            throw ApiException.badRequest("Dòng họ này đang bị khóa");
        }
        if (familyRoleRepository.findByFamilyIdAndUserId(f.getId(), userId).isPresent()) {
            throw ApiException.badRequest("Bạn đã là thành viên của dòng họ này");
        }
        if (joinRequestRepository.existsByFamilyIdAndUserIdAndStatus(f.getId(), userId, "PENDING")) {
            throw ApiException.badRequest("Bạn đã gửi yêu cầu và đang chờ duyệt");
        }
        JoinRequest jr = new JoinRequest();
        jr.setFamilyId(f.getId());
        jr.setUserId(userId);
        jr.setMessage(req.message());
        if (req.claimedMemberId() != null) {
            memberRepository.findByIdAndFamilyId(req.claimedMemberId(), f.getId())
                    .ifPresent(m -> jr.setClaimedMemberId(m.getId()));
        }
        joinRequestRepository.save(jr);

        User u = userRepository.findById(userId).orElseThrow();
        notificationService.notifyFamily(f.getId(), FamilyRoleType.ADMIN, "Yêu cầu tham gia mới",
                u.getFullName() + " (" + u.getUsername() + ") xin tham gia dòng họ " + f.getName(), "join:" + jr.getId());
        return toDtos(List.of(jr)).get(0);
    }

    @Transactional(readOnly = true)
    public List<JoinRequestDto> listForFamily(Long familyId, String status) {
        permissionService.require(familyId, FamilyRoleType.ADMIN);
        List<JoinRequest> list = status == null
                ? joinRequestRepository.findByFamilyIdOrderByCreatedAtDesc(familyId)
                : joinRequestRepository.findByFamilyIdAndStatusOrderByCreatedAtDesc(familyId, status);
        return toDtos(list);
    }

    @Transactional(readOnly = true)
    public List<JoinRequestDto> mine() {
        return toDtos(joinRequestRepository.findByUserIdOrderByCreatedAtDesc(CurrentUser.id()));
    }

    @Transactional
    public void approve(Long familyId, Long requestId, ApproveRequest req) {
        PermissionService.Access a = permissionService.require(familyId, FamilyRoleType.ADMIN);
        JoinRequest jr = findPending(familyId, requestId);
        if (req.role() == FamilyRoleType.OWNER
                || (req.role() == FamilyRoleType.ADMIN && !a.atLeast(FamilyRoleType.OWNER))) {
            throw ApiException.badRequest("Không thể cấp vai trò này khi duyệt");
        }
        if (req.role() == FamilyRoleType.BRANCH_MANAGER) {
            throw ApiException.badRequest("Hãy duyệt với vai trò Thành viên, sau đó cấp quyền Trưởng chi trong mục Phân quyền");
        }
        if (familyRoleRepository.findByFamilyIdAndUserId(familyId, jr.getUserId()).isEmpty()) {
            FamilyRole r = new FamilyRole();
            r.setFamilyId(familyId);
            r.setUserId(jr.getUserId());
            r.setRole(req.role());
            familyRoleRepository.save(r);
        }
        if (req.memberId() != null) {
            familyService.linkUserToMember(familyId, jr.getUserId(), req.memberId());
        }
        jr.setStatus("APPROVED");
        jr.setReviewedBy(a.userId());
        jr.setReviewedAt(Instant.now());
        notificationService.notifyUser(jr.getUserId(), familyId, "Yêu cầu tham gia đã được duyệt",
                "Bạn đã trở thành thành viên của dòng họ " + a.family().getName(), null);
    }

    @Transactional
    public void reject(Long familyId, Long requestId) {
        PermissionService.Access a = permissionService.require(familyId, FamilyRoleType.ADMIN);
        JoinRequest jr = findPending(familyId, requestId);
        jr.setStatus("REJECTED");
        jr.setReviewedBy(a.userId());
        jr.setReviewedAt(Instant.now());
        notificationService.notifyUser(jr.getUserId(), familyId, "Yêu cầu tham gia bị từ chối",
                "Yêu cầu tham gia dòng họ " + a.family().getName() + " không được chấp thuận", null);
    }

    private JoinRequest findPending(Long familyId, Long requestId) {
        return joinRequestRepository.findById(requestId)
                .filter(j -> j.getFamilyId().equals(familyId) && "PENDING".equals(j.getStatus()))
                .orElseThrow(() -> ApiException.notFound("yêu cầu đang chờ duyệt"));
    }

    private List<JoinRequestDto> toDtos(List<JoinRequest> list) {
        Map<Long, User> users = userRepository.findAllById(list.stream().map(JoinRequest::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Family> families = familyRepository.findAllById(list.stream().map(JoinRequest::getFamilyId).toList())
                .stream().collect(Collectors.toMap(Family::getId, Function.identity()));
        Map<Long, FamilyMember> members = memberRepository.findAllById(list.stream().map(JoinRequest::getClaimedMemberId)
                        .filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(FamilyMember::getId, Function.identity()));
        return list.stream().map(j -> {
            User u = users.get(j.getUserId());
            Family f = families.get(j.getFamilyId());
            FamilyMember m = j.getClaimedMemberId() == null ? null : members.get(j.getClaimedMemberId());
            return new JoinRequestDto(j.getId(), j.getFamilyId(), f == null ? null : f.getName(), j.getUserId(),
                    u == null ? null : u.getUsername(), u == null ? null : u.getFullName(),
                    u == null ? null : u.getEmail(), u == null ? null : u.getPhone(),
                    j.getClaimedMemberId(), m == null ? null : m.getFullName(), j.getMessage(), j.getStatus(),
                    j.getCreatedAt());
        }).toList();
    }
}
