package vn.giapha.marital.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.giapha.marital.MarriageStatus;
import vn.giapha.member.Gender;

import java.time.LocalDate;
import java.util.List;

/** Mot doi vo/chong cua thanh vien, kem danh sach con chung */
@Schema(description = "Một đời vợ/chồng của thành viên")
public record SpouseResponseDto(
        @Schema(description = "Id quan hệ hôn nhân") Long relationId,
        @Schema(description = "Id người vợ/chồng") Long spouseId,
        String fullName,
        String otherName,
        Gender gender,
        @Schema(description = "true nếu người này thuộc huyết thống dòng họ") boolean bloodLine,
        boolean alive,
        LocalDate birthDateSolar,
        LocalDate deathDateSolar,
        String avatarUrl,
        int marriageOrder,
        @Schema(description = "Danh xưng theo thứ tự", example = "Vợ cả (Chính thất)") String title,
        MarriageStatus status,
        String note,
        @Schema(description = "Con chung của hai người, theo thứ tự con trưởng → con út") List<ChildSummary> children) {

    @Schema(description = "Tóm tắt một người con")
    public record ChildSummary(Long id, String fullName, Gender gender, Integer birthOrder, boolean alive) {
    }

    /**
     * Danh xung theo thu tu ket hon, nhin tu phia thanh vien dang xem.
     *
     * @param spouseGender gioi tinh cua nguoi vo/chong
     */
    public static String titleOf(Gender spouseGender, int order) {
        if (spouseGender == Gender.FEMALE) {
            return switch (order) {
                case 1 -> "Vợ cả (Chính thất)";
                case 2 -> "Vợ hai (Kế thất)";
                case 3 -> "Vợ ba";
                default -> "Vợ thứ " + order;
            };
        }
        return order == 1 ? "Chồng" : "Chồng thứ " + order;
    }
}
