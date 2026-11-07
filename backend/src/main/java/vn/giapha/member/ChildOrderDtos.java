package vn.giapha.member;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ChildOrderDtos {
    private ChildOrderDtos() {
    }

    @Schema(description = "Một người con trong nhóm anh chị em")
    public record SiblingDto(
            Long id,
            String fullName,
            Gender gender,
            int birthOrder,
            @Schema(example = "Con cả · Trưởng nam") String label,
            Long fatherId,
            Long motherId,
            boolean alive) {

        public static SiblingDto of(FamilyMember m, String label) {
            return new SiblingDto(m.getId(), m.getFullName(), m.getGender(), m.getBirthOrder(), label,
                    m.getFatherId(), m.getMotherId(), m.isAlive());
        }
    }

    @Schema(description = "Gợi ý thứ tự cho người con tiếp theo")
    public record NextBirthOrderResponse(
            @Schema(description = "Người đại diện nhóm anh chị em (cha thuộc huyết thống, hoặc mẹ nếu cha là con rể)")
            Long groupParentId,
            String groupParentName,
            @Schema(description = "Số con hiện có trong nhóm") int childCount,
            @Schema(description = "birth_order lớn nhất hiện tại") int maxBirthOrder,
            @Schema(description = "Gợi ý = max + 1", example = "5") int nextBirthOrder,
            @Schema(description = "Các con hiện có, theo thứ tự sinh") List<SiblingDto> siblings) {
    }

    @Schema(description = "Thứ tự mới của toàn bộ con trong nhóm: phần tử đầu là con cả")
    public record ReorderChildrenRequest(
            @NotEmpty @Size(max = 99) List<@NotNull Long> childIds) {
    }
}
