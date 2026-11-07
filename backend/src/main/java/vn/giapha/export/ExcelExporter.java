package vn.giapha.export;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bao cao gia pha -> Excel .xlsx (Apache POI): dong tieu de co dinh, loc tu dong, thut le theo nhanh */
@Component
public class ExcelExporter {
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final byte[] RED = {(byte) 0x7c, 0x23, 0x23};
    private static final byte[] STRIPE = {(byte) 0xfb, (byte) 0xf6, (byte) 0xee};

    public byte[] render(ExportReport r) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Gia phả");
            List<ExportField> fields = r.options().orderedFields();
            int cols = fields.size() + 1;
            Styles st = new Styles(wb);

            // Tieu de
            Row t = sheet.createRow(0);
            t.setHeightInPoints(26);
            cell(t, 0, "GIA PHẢ – " + r.family().getName(), st.title);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, cols - 1));
            Row m = sheet.createRow(1);
            cell(m, 0, "Lập ngày " + r.generatedOn().format(DMY) + " · " + r.memberCount() + " người · "
                    + (r.options().sort() == ExportOptions.Sort.BRANCH ? "Xếp theo nhánh" : "Xếp theo đời")
                    + (r.options().publicView() ? " · Bản công khai (đã che thông tin riêng tư)" : " · Bản nội bộ"), st.meta);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, cols - 1));

            // Dong tieu de cot
            int headerRow = 3;
            Row h = sheet.createRow(headerRow);
            h.setHeightInPoints(22);
            cell(h, 0, "STT", st.header);
            sheet.setColumnWidth(0, 6 * 256);
            for (int i = 0; i < fields.size(); i++) {
                cell(h, i + 1, fields.get(i).label, st.header);
                sheet.setColumnWidth(i + 1, fields.get(i).excelWidth * 256);
            }

            int rowIdx = headerRow + 1;
            int stripe = 0;
            for (ExportReport.Row row : r.rows()) {
                if (!row.spouseRow()) stripe++;
                boolean shade = stripe % 2 == 0;
                Row x = sheet.createRow(rowIdx++);
                Cell no = x.createCell(0);
                if (row.no() != null) no.setCellValue(row.no());
                no.setCellStyle(st.body(shade, false, 0, HorizontalAlignment.CENTER));
                for (int i = 0; i < fields.size(); i++) {
                    ExportField f = fields.get(i);
                    String v = row.value(f);
                    if (f == ExportField.FULL_NAME) {
                        int indent = r.options().sort() == ExportOptions.Sort.BRANCH ? Math.min(row.depth(), 15) : 0;
                        cell(x, i + 1, v, st.body(shade, row.spouseRow(), indent, HorizontalAlignment.LEFT));
                    } else if (f == ExportField.GENERATION && !v.isEmpty()) {
                        Cell c = x.createCell(i + 1);
                        c.setCellValue(Integer.parseInt(v)); // so -> loc / sap xep dung trong Excel
                        c.setCellStyle(st.body(shade, false, 0, HorizontalAlignment.CENTER));
                    } else {
                        cell(x, i + 1, v, st.body(shade, v.contains(ExportService.HIDDEN), 0,
                                f == ExportField.GENDER ? HorizontalAlignment.CENTER : HorizontalAlignment.LEFT));
                    }
                }
            }
            sheet.createFreezePane(2, headerRow + 1); // co dinh STT + Ho ten va dong tieu de
            sheet.setAutoFilter(new CellRangeAddress(headerRow, Math.max(headerRow, rowIdx - 1), 0, cols - 1));
            sheet.getPrintSetup().setLandscape(true);
            sheet.getPrintSetup().setPaperSize(r.options().paper() == ExportOptions.Paper.A3
                    ? PrintSetup.A3_PAPERSIZE : PrintSetup.A4_PAPERSIZE);
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);
            sheet.setRepeatingRows(new CellRangeAddress(headerRow, headerRow, -1, -1));

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    /** Bo style dung lai (Excel gioi han so style trong mot file) */
    private static final class Styles {
        final XSSFWorkbook wb;
        final CellStyle title;
        final CellStyle meta;
        final CellStyle header;
        final XSSFFont normal;
        final XSSFFont italic;
        final Map<String, CellStyle> cache = new HashMap<>();

        Styles(XSSFWorkbook wb) {
            this.wb = wb;
            XSSFFont tf = wb.createFont();
            tf.setBold(true);
            tf.setFontHeightInPoints((short) 16);
            tf.setColor(new XSSFColor(RED, null));
            title = wb.createCellStyle();
            title.setFont(tf);

            XSSFFont mf = wb.createFont();
            mf.setItalic(true);
            mf.setFontHeightInPoints((short) 10);
            meta = wb.createCellStyle();
            meta.setFont(mf);

            XSSFFont hf = wb.createFont();
            hf.setBold(true);
            hf.setColor(IndexedColors.WHITE.getIndex());
            XSSFCellStyle hs = wb.createCellStyle();
            hs.setFont(hf);
            hs.setFillForegroundColor(new XSSFColor(RED, null));
            hs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            hs.setAlignment(HorizontalAlignment.CENTER);
            hs.setVerticalAlignment(VerticalAlignment.CENTER);
            hs.setWrapText(true);
            header = hs;

            normal = wb.createFont();
            italic = wb.createFont();
            italic.setItalic(true);
            italic.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        }

        CellStyle body(boolean shade, boolean muted, int indent, HorizontalAlignment align) {
            return cache.computeIfAbsent(shade + "|" + muted + "|" + indent + "|" + align, k -> {
                XSSFCellStyle s = wb.createCellStyle();
                s.setFont(muted ? italic : normal);
                s.setWrapText(true);
                s.setVerticalAlignment(VerticalAlignment.TOP);
                s.setAlignment(align);
                s.setIndention((short) indent);
                s.setBorderBottom(BorderStyle.HAIR);
                if (shade) {
                    s.setFillForegroundColor(new XSSFColor(STRIPE, null));
                    s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                }
                return s;
            });
        }
    }
}
