package vn.giapha.export;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Tuy chon xuat bao cao.
 *
 * @param fields     cac cot can xuat (Ho ten luon co)
 * @param sort       BRANCH: theo nhanh kieu pha ky (moi nguoi -> vo/chong -> cac con); GENERATION: theo tung doi
 * @param paper      kho giay PDF
 * @param landscape  giay ngang
 * @param publicView true: ap dung quyen rieng tu nhu tai khoan Khach (ban de phat hanh / cong khai)
 */
public record ExportOptions(Set<ExportField> fields, Sort sort, Paper paper, boolean landscape, boolean publicView) {

    public enum Sort { BRANCH, GENERATION }

    public enum Paper { A4, A3 }

    /** Cot mac dinh khi khong chon gi */
    public static final Set<ExportField> DEFAULT_FIELDS = EnumSet.of(
            ExportField.FULL_NAME, ExportField.GENERATION, ExportField.GENDER,
            ExportField.BIRTH, ExportField.DEATH, ExportField.ROLE);

    public static ExportOptions of(Collection<ExportField> fields, Sort sort, Paper paper, boolean landscape, boolean publicView) {
        Set<ExportField> set = fields == null || fields.isEmpty() ? EnumSet.copyOf(DEFAULT_FIELDS) : EnumSet.copyOf(fields);
        set.add(ExportField.FULL_NAME); // khong the bo ho ten
        return new ExportOptions(set, sort == null ? Sort.BRANCH : sort, paper == null ? Paper.A4 : paper, landscape, publicView);
    }

    /** Cac cot theo dung thu tu hien thi */
    public List<ExportField> orderedFields() {
        return EnumSet.copyOf(fields).stream().toList();
    }
}
