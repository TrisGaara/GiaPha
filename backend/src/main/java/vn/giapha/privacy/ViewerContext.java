package vn.giapha.privacy;

import vn.giapha.family.FamilyRoleType;
import vn.giapha.family.PermissionService.Access;
import vn.giapha.member.FamilyMember;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Ngu canh "ai dang xem" trong mot dong ho - tinh mot lan cho moi request roi dung cho moi thanh vien.
 *
 * <h3>Ma tran quyen xem</h3>
 * <pre>
 *                         | Nguoi an khoi cay | Truong chi tiet (ngay sinh, gio, lien he, tieu su)
 * ------------------------+-------------------+---------------------------------------------------
 * FULL (Admin/ban than/   | Thay (dang mo)    | Thay tat ca
 *       Truong chi nhanh) |                   |
 * MEMBER                  | Khong thay        | Thay tat ca
 * GUEST (VIEWER)          | Khong thay        | Ho so khong cong khai -> an het; nguoc lai an theo tung co hide_*
 * </pre>
 */
public final class ViewerContext {
    private final Access access;
    /** Thanh vien nguoi xem duoc sua; null = tat ca (Admin tro len) */
    private final Set<Long> editableScope;
    /** Cac nut tren cay gan voi tai khoan nguoi xem */
    private final Set<Long> selfMemberIds;

    public ViewerContext(Access access, Set<Long> editableScope, Set<Long> selfMemberIds) {
        this.access = access;
        this.editableScope = editableScope;
        this.selfMemberIds = selfMemberIds;
    }

    public Access access() {
        return access;
    }

    public Long familyId() {
        return access.family().getId();
    }

    /** Admin tro len: khong can loc gi */
    public boolean seesEverything() {
        return editableScope == null;
    }

    public boolean canEdit(FamilyMember m) {
        return editableScope == null || editableScope.contains(m.getId());
    }

    public ViewerTier tierFor(FamilyMember m) {
        if (canEdit(m) || selfMemberIds.contains(m.getId())) {
            return ViewerTier.FULL;
        }
        return access.atLeast(FamilyRoleType.MEMBER) ? ViewerTier.MEMBER : ViewerTier.GUEST;
    }

    /** Nguoi xem co thay nut nay tren cay / trong danh sach khong */
    public boolean canSeeNode(FamilyMember m) {
        return m.isVisibleOnTree() || tierFor(m) == ViewerTier.FULL;
    }

    /** Cac nhom truong phai che khi tra ve cho nguoi xem nay */
    public Set<MaskedField> maskedFields(FamilyMember m) {
        if (tierFor(m) != ViewerTier.GUEST) {
            return EnumSet.noneOf(MaskedField.class);
        }
        if (!m.isPublicProfile()) {
            return EnumSet.allOf(MaskedField.class);
        }
        Set<MaskedField> masked = EnumSet.noneOf(MaskedField.class);
        if (m.isHideBirthDate()) masked.add(MaskedField.BIRTH_DATE);
        if (m.isHideDeathDate()) masked.add(MaskedField.DEATH_DATE);
        if (m.isHideContactInfo()) masked.add(MaskedField.CONTACT_INFO);
        if (m.isHideBio()) masked.add(MaskedField.BIO);
        return masked;
    }

    /** Nhung nguoi luon hien voi nguoi xem du bi an (dung trong Specification) */
    public Set<Long> alwaysVisibleIds() {
        Set<Long> ids = new HashSet<>(selfMemberIds);
        if (editableScope != null) {
            ids.addAll(editableScope);
        }
        return ids;
    }
}
