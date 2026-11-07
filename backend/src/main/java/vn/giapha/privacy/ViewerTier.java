package vn.giapha.privacy;

/** Muc do nguoi dang xem duoc phep thay doi voi MOT thanh vien cu the */
public enum ViewerTier {
    /** Thay tat ca, ke ca nguoi bi an khoi cay: Super Admin, Toc truong, Ban quan tri, chinh nguoi do, Truong chi trong nhanh */
    FULL,
    /** Thanh vien chinh thuc: thay moi truong chi tiet, nhung khong thay nguoi bi an khoi cay */
    MEMBER,
    /** Khach (VIEWER): bi ap dung cac co hide_* va is_public_profile */
    GUEST
}
