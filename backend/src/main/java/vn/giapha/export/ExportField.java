package vn.giapha.export;

/**
 * Cac cot co the chon khi xuat bao cao gia pha.
 * Thu tu khai bao = thu tu cot trong file.
 */
public enum ExportField {
    FULL_NAME("Họ và tên", 18, 30),
    GENERATION("Đời", 5, 7),
    GENDER("Giới tính", 6, 10),
    BIRTH("Ngày sinh", 13, 24),
    DEATH("Ngày mất / Ngày giỗ", 15, 30),
    ROLE("Vai trò / Quan hệ", 15, 28),
    PARENTS("Cha / Mẹ", 15, 28),
    SPOUSES("Vợ / Chồng", 15, 30),
    HOMETOWN("Quê quán / Nghề nghiệp", 13, 26),
    CONTACT("Liên hệ", 15, 30),
    NOTE("Tiểu sử / Ghi chú", 22, 50);

    /** Tieu de cot */
    public final String label;
    /** Do rong tuong doi cua cot trong PDF */
    public final float pdfWidth;
    /** Do rong cot Excel (so ky tu) */
    public final int excelWidth;

    ExportField(String label, float pdfWidth, int excelWidth) {
        this.label = label;
        this.pdfWidth = pdfWidth;
        this.excelWidth = excelWidth;
    }
}
