package vn.giapha.family;

/** Quy uoc sap xep cac con duoi cha me khi hien thi (cay pha he, danh sach) */
public enum ChildSortMode {
    /** Theo thu tu sinh: con ca ben trai -> con ut ben phai */
    BIRTH_ORDER,
    /** Con trai truoc (theo thu tu sinh), roi den con gai (theo thu tu sinh) */
    SONS_FIRST
}
