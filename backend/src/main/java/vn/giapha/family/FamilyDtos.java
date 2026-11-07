package vn.giapha.family;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class FamilyDtos {
    private FamilyDtos() {
    }

    public record FamilyRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 50) String surname,
            String description,
            @Size(max = 255) String origin,
            @Size(max = 255) String ancestralHallAddress,
            @Size(max = 50) String telegramChatId,
            /* null = giu nguyen */
            ChildSortMode childSortMode) {
    }

    public record FamilyDto(
            Long id, String name, String surname, String description, String origin, String ancestralHallAddress,
            FamilyStatus status, FamilyRoleType myRole, Long myBranchRootMemberId, ChildSortMode childSortMode,
            /* chi tra ve cho Admin */
            String joinCode, String telegramChatId,
            long memberCount, long userCount, Instant createdAt) {
    }

    /** Thong tin toi thieu khi tra cuu bang ma tham gia */
    public record FamilyPreview(Long id, String name, String surname, String origin, FamilyStatus status) {
    }

    public record FamilyRoleDto(Long id, Long userId, String username, String fullName, String email,
                                FamilyRoleType role, Long branchRootMemberId, String branchRootName,
                                Long linkedMemberId, String linkedMemberName, Instant createdAt) {
    }

    public record UpdateRoleRequest(@NotNull FamilyRoleType role, Long branchRootMemberId, Long linkedMemberId) {
    }
}
