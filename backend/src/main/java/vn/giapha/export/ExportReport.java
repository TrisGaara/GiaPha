package vn.giapha.export;

import vn.giapha.family.Family;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Du lieu bao cao da chon cot, sap xep va che theo quyen rieng tu - dung chung cho PDF va Excel */
public record ExportReport(Family family, ExportOptions options, List<Row> rows, int memberCount, LocalDate generatedOn) {

    /**
     * Mot dong trong bao cao.
     *
     * @param no         so thu tu (chi danh cho nguoi huyet thong; dong vo/chong = null)
     * @param depth      do sau trong nhanh (de thut le ten khi sap theo nhanh)
     * @param spouseRow  dong vo/chong (con dau / con re) nam ngay duoi nguoi huyet thong
     * @param generation doi thu (de nhom khi sap theo doi)
     * @param values     gia tri tung cot (chuoi rong = khong co, "(ẩn)" = bi che)
     */
    public record Row(Integer no, int depth, boolean spouseRow, Integer generation, Map<ExportField, String> values) {
        public String value(ExportField f) {
            return values.getOrDefault(f, "");
        }
    }
}
