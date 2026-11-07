package vn.giapha.marital.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import vn.giapha.marital.MarriageStatus;

import java.time.LocalDate;

/**
 * Them vo/chong cho mot thanh vien. Chon <b>mot trong hai</b> cach:
 * <ul>
 *     <li>{@code existingSpouseId}: gan voi mot nguoi da co trong pha he</li>
 *     <li>{@code newSpouse}: tao moi mot thanh vien ngoai toc (con dau / con re) roi tao quan he hon nhan</li>
 * </ul>
 */
@Schema(description = "Thêm vợ/chồng cho thành viên (chọn người có sẵn hoặc tạo mới con dâu/con rể)")
public record AddSpouseRequest(
        @Schema(description = "Id người đã có trong phả hệ", example = "12")
        Long existingSpouseId,

        @Schema(description = "Thông tin người mới (con dâu / con rể)")
        @Valid
        NewSpouseInfo newSpouse,

        @Schema(description = "Thứ tự kết hôn: 1 = Vợ cả/Chính thất, 2 = Vợ hai/Kế thất... Bỏ trống để tự tăng", example = "1")
        @Min(1) @Max(20)
        Integer marriageOrder,

        @Schema(description = "Trạng thái hôn nhân, mặc định ACTIVE")
        MarriageStatus status,

        @Schema(description = "Ghi chú", example = "Kết hôn năm 1965 tại quê nhà")
        @Size(max = 255)
        String note) {

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Phải chọn người có sẵn (existingSpouseId) hoặc nhập người mới (newSpouse), và chỉ một trong hai")
    public boolean isExactlyOneSpouseSource() {
        return (existingSpouseId != null) ^ (newSpouse != null);
    }

    /** Thong tin toi thieu de tao moi con dau / con re. Gioi tinh tu suy ra (nguoc voi thanh vien). */
    @Schema(description = "Thông tin con dâu / con rể mới")
    public record NewSpouseInfo(
            @NotBlank @Size(max = 150) @Schema(example = "Trần Thị Mai") String fullName,
            @Size(max = 150) String otherName,
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
            @Size(max = 500) String avatarUrl,
            @Valid vn.giapha.privacy.PrivacySettings privacy) {
    }
}
