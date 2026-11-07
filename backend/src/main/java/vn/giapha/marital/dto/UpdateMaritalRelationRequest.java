package vn.giapha.marital.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.giapha.marital.MarriageStatus;

@Schema(description = "Cập nhật thứ tự / trạng thái của một quan hệ hôn nhân")
public record UpdateMaritalRelationRequest(
        @NotNull @Min(1) @Max(20) Integer marriageOrder,
        @NotNull MarriageStatus status,
        @Size(max = 255) String note) {
}
