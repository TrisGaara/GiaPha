package vn.giapha.marital.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import vn.giapha.member.Gender;

import java.time.LocalDate;

/**
 * Tao con va lien ket voi cha / me.
 * <p>
 * Rang buoc (kiem tra o tang service, xem {@code ParentageValidator}):
 * <ul>
 *     <li>Phai co it nhat cha hoac me; cha la nam, me la nu, cung dong ho.</li>
 *     <li>Neu co ca hai: me phai nam trong danh sach cac doi vo cua cha (hoac nguoc lai neu con theo ho me).</li>
 *     <li>It nhat mot trong hai phai thuoc huyet thong dong ho.</li>
 * </ul>
 */
@Schema(description = "Thêm con và liên kết cha/mẹ")
public record AddChildRequest(
        @Schema(description = "Id người cha", example = "5") Long fatherId,
        @Schema(description = "Id người mẹ (phải là một đời vợ của cha)", example = "6") Long motherId,

        @NotBlank @Size(max = 150) @Schema(example = "Nguyễn Văn An") String fullName,
        @Size(max = 150) String otherName,
        @NotNull Gender gender,
        @Schema(description = """
                Thứ tự sinh trong nhóm anh chị em (1 = con cả).
                Bỏ trống: tự động = thứ tự lớn nhất hiện tại + 1 (xếp cuối).
                Có nhập: chèn vào đúng vị trí, các anh chị em từ vị trí đó trở đi lùi xuống một bậc.
                Xem gợi ý tại GET /members/{parentId}/next-birth-order""", example = "3")
        @Min(1) @Max(99) Integer birthOrder,

        LocalDate birthDateSolar,
        @Min(1) @Max(30) Integer birthLunarDay,
        @Min(1) @Max(12) Integer birthLunarMonth,
        Integer birthLunarYear,
        boolean birthLunarLeap,

        @Schema(description = "Mặc định là còn sống") Boolean alive,
        LocalDate deathDateSolar,
        @Min(1) @Max(30) Integer deathLunarDay,
        @Min(1) @Max(12) Integer deathLunarMonth,
        Integer deathLunarYear,
        boolean deathLunarLeap,
        @Size(max = 255) String burialPlace,

        @Size(max = 255) String hometown,
        @Size(max = 255) String occupation,
        String biography,
        String achievements,
        @Schema(description = "Cấu hình quyền riêng tư (bỏ trống = mặc định)") @jakarta.validation.Valid
        vn.giapha.privacy.PrivacySettings privacy) {

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Phải chọn ít nhất cha hoặc mẹ")
    public boolean isAtLeastOneParent() {
        return fatherId != null || motherId != null;
    }
}
