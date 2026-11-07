package vn.giapha.family;

/** Vai tro cua nguoi dung trong mot dong ho, sap xep theo muc quyen giam dan */
public enum FamilyRoleType {
    /** Toc truong / nguoi tao dong ho */
    OWNER(100),
    /** Ban quan tri dong ho */
    ADMIN(80),
    /** Truong chi / truong phong: quan ly mot nhanh */
    BRANCH_MANAGER(60),
    /** Thanh vien chinh thuc */
    MEMBER(40),
    /** Khach - chi xem */
    VIEWER(20);

    private final int level;

    FamilyRoleType(int level) {
        this.level = level;
    }

    public boolean atLeast(FamilyRoleType other) {
        return this.level >= other.level;
    }
}
