package vn.giapha.marital.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.giapha.marital.MaritalRelation;
import vn.giapha.marital.MarriageStatus;

import java.time.Instant;

/** Ban ghi hon nhan "phang" - dung cho du lieu cay pha he */
@Schema(description = "Quan hệ hôn nhân")
public record MaritalRelationDto(Long id, Long husbandId, Long wifeId, int marriageOrder,
                                 MarriageStatus status, String note, Instant createdAt, Instant updatedAt) {

    public static MaritalRelationDto of(MaritalRelation r) {
        return new MaritalRelationDto(r.getId(), r.getHusbandId(), r.getWifeId(), r.getMarriageOrder(),
                r.getStatus(), r.getNote(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
