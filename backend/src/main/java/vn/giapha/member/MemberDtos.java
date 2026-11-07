package vn.giapha.member;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import vn.giapha.lunar.LunarCalendar;
import vn.giapha.marital.dto.MaritalRelationDto;
import vn.giapha.privacy.MaskedField;
import vn.giapha.privacy.PrivacySettings;

import java.time.LocalDate;
import java.util.List;

public final class MemberDtos {
    private MemberDtos() {
    }

    /** Tao / cap nhat thanh vien (tuong ung UpdateFamilyMemberRequest) */
    public record MemberRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 150) String otherName,
            @NotNull Gender gender,
            @Min(1) @Max(200) Integer generation,
            /* null = tu dong xep cuoi (max + 1); co gia tri = chen vao vi tri do */
            @Min(1) @Max(99) Integer birthOrder,
            Long fatherId,
            Long motherId,
            /* null = true (thuoc huyet thong); false = con dau / con re */
            Boolean bloodLine,
            LocalDate birthDateSolar,
            @Min(1) @Max(30) Integer birthLunarDay,
            @Min(1) @Max(12) Integer birthLunarMonth,
            Integer birthLunarYear,
            boolean birthLunarLeap,
            Boolean alive,
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
            @Size(max = 30) @Pattern(regexp = "^[0-9+()\\s.-]*$", message = "chỉ gồm số và + ( ) . -") String phone,
            @Email @Size(max = 150) String email,
            @Size(max = 255) String address,
            /* null = giu nguyen (tao moi: mac dinh) */
            @Valid PrivacySettings privacy) {

        public boolean isBloodLine() {
            return bloodLine == null || bloodLine;
        }
    }

    public record LunarInfo(Integer day, Integer month, Integer year, boolean leap, String label) {
        public static LunarInfo of(Integer day, Integer month, Integer year, boolean leap) {
            if (day == null || month == null) {
                return null;
            }
            String label = day + "/" + month + (leap ? " (nhuận)" : "")
                    + (year != null ? " năm " + LunarCalendar.canChiYear(year) : "");
            return new LunarInfo(day, month, year, leap, label);
        }
    }

    /**
     * Thanh vien tra ve cho Frontend (tuong ung FamilyMemberResponseDto).
     * Cac truong bi che theo quyen rieng tu = null, va nhom tuong ung co trong {@code hiddenFields}.
     * Tao bang {@link MemberMapper}.
     */
    public record MemberDto(
            Long id, Long familyId, Long userId,
            String fullName, String otherName, Gender gender,
            Integer generation, Integer birthOrder,
            /* Danh xung theo thu tu sinh, vd "Con cả · Trưởng nam" */
            String birthOrderLabel,
            Long fatherId, Long motherId, boolean bloodLine,
            LocalDate birthDateSolar, LunarInfo birthLunar,
            boolean alive, LocalDate deathDateSolar, LunarInfo deathLunar, String burialPlace,
            String hometown, String occupation, String biography, String achievements, String avatarUrl,
            String phone, String email, String address,
            @Schema(description = "Cấu hình riêng tư - chỉ trả về cho người được sửa hồ sơ")
            PrivacySettings privacy,
            @Schema(description = "Nhóm trường đã bị che với người xem hiện tại")
            List<MaskedField> hiddenFields,
            @Schema(description = "true: nút ẩn danh giữ cấu trúc cây (người thật đã bị ẩn)")
            boolean placeholder,
            boolean canEdit) {
    }

    /** Toan bo du lieu de ve cay pha he */
    public record TreeDto(List<MemberDto> members, List<MaritalRelationDto> maritalRelations) {
    }
}
