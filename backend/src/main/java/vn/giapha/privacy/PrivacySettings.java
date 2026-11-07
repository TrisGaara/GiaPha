package vn.giapha.privacy;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.giapha.member.FamilyMember;

/**
 * Cau hinh quyen rieng tu cua mot thanh vien.
 * Dung cho ca request (truong null = giu nguyen) va response (chi tra ve cho nguoi duoc sua ho so).
 */
@Schema(description = "Cấu hình quyền riêng tư (request: trường null = giữ nguyên)")
public record PrivacySettings(
        @Schema(description = "Hiện trên cây phả hệ. Chỉ Ban quản trị / Trưởng chi được đổi") Boolean visibleOnTree,
        @Schema(description = "Công khai chi tiết với khách (Viewer)") Boolean publicProfile,
        @Schema(description = "Ẩn ngày sinh với khách") Boolean hideBirthDate,
        @Schema(description = "Ẩn ngày mất, ngày giỗ, nơi an táng với khách") Boolean hideDeathDate,
        @Schema(description = "Ẩn số điện thoại, email, địa chỉ với khách (mặc định ẩn)") Boolean hideContactInfo,
        @Schema(description = "Ẩn tiểu sử, thành tựu, nghề nghiệp với khách") Boolean hideBio) {

    public static PrivacySettings of(FamilyMember m) {
        return new PrivacySettings(m.isVisibleOnTree(), m.isPublicProfile(), m.isHideBirthDate(),
                m.isHideDeathDate(), m.isHideContactInfo(), m.isHideBio());
    }

    /** Yeu cau nay co lam thay doi trang thai an/hien tren cay khong */
    public boolean changesTreeVisibility(FamilyMember m) {
        return visibleOnTree != null && visibleOnTree != m.isVisibleOnTree();
    }

    /** Ghi cac truong khac null len entity (khong kiem tra quyen - xem PrivacyService#applySettings) */
    void copyTo(FamilyMember m) {
        if (visibleOnTree != null) m.setVisibleOnTree(visibleOnTree);
        if (publicProfile != null) m.setPublicProfile(publicProfile);
        if (hideBirthDate != null) m.setHideBirthDate(hideBirthDate);
        if (hideDeathDate != null) m.setHideDeathDate(hideDeathDate);
        if (hideContactInfo != null) m.setHideContactInfo(hideContactInfo);
        if (hideBio != null) m.setHideBio(hideBio);
    }
}
