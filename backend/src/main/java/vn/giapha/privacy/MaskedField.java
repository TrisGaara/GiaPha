package vn.giapha.privacy;

/** Cac nhom truong co the bi che (tra ve null) voi nguoi xem */
public enum MaskedField {
    /** birthDateSolar, birthLunar */
    BIRTH_DATE,
    /** deathDateSolar, deathLunar (ngay gio), burialPlace */
    DEATH_DATE,
    /** phone, email, address */
    CONTACT_INFO,
    /** biography, achievements, occupation */
    BIO,
    /** Ho so khong cong khai: them hometown, avatarUrl, userId */
    PROFILE
}
