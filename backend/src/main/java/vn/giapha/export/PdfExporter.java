package vn.giapha.export;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.giapha.family.Family;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Bao cao gia pha dang danh sach -> PDF (OpenPDF).
 * <p>
 * Font Noto Serif (SIL OFL) duoc nhung vao file: font chuan cua PDF khong co dau tieng Viet.
 * Trinh bay: tieu de trang trong, bang co dong tieu de lap lai moi trang, chan trang "Trang x / y".
 */
@Component
public class PdfExporter {
    private static final Color RED = new Color(0x7c, 0x23, 0x23);
    private static final Color GOLD = new Color(0xb7, 0x79, 0x1f);
    private static final Color STRIPE = new Color(0xfb, 0xf6, 0xee);
    private static final Color GROUP = new Color(0xf3, 0xe4, 0xc6);
    private static final Color GRID = new Color(0xd9, 0xc7, 0xa7);
    private static final Color MUTED = new Color(0x6b, 0x62, 0x5a);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BaseFont regular = PdfFonts.REGULAR;
    private final BaseFont bold = PdfFonts.BOLD;
    private final BaseFont italic = PdfFonts.ITALIC;

    public byte[] render(ExportReport report) {
        ExportOptions opt = report.options();
        Rectangle page = opt.paper() == ExportOptions.Paper.A3 ? PageSize.A3 : PageSize.A4;
        if (opt.landscape()) page = page.rotate();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(page, 36, 36, 40, 44);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(new Footer(report));
        doc.addTitle("Gia phả " + report.family().getName());
        doc.addCreator("Gia Phả Việt");
        doc.open();
        writeHeading(doc, report);
        writeTable(doc, report);
        doc.close();
        return out.toByteArray();
    }

    // ---------------------------------------------------------------------

    private void writeHeading(Document doc, ExportReport r) {
        Family f = r.family();
        Paragraph title = new Paragraph("GIA PHẢ", new Font(bold, 26, Font.NORMAL, RED));
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);

        Paragraph name = new Paragraph(f.getName(), new Font(bold, 15, Font.NORMAL, GOLD));
        name.setAlignment(Element.ALIGN_CENTER);
        name.setSpacingBefore(2);
        doc.add(name);

        String meta = String.join("  ·  ", nonEmpty(
                "Họ " + f.getSurname(),
                StringUtils.hasText(f.getOrigin()) ? "Nguyên quán: " + f.getOrigin() : null,
                StringUtils.hasText(f.getAncestralHallAddress()) ? "Nhà thờ tổ: " + f.getAncestralHallAddress() : null));
        Paragraph m = new Paragraph(meta, new Font(italic, 10, Font.NORMAL, MUTED));
        m.setAlignment(Element.ALIGN_CENTER);
        m.setSpacingBefore(4);
        doc.add(m);

        Paragraph rule = new Paragraph(new Chunk(new com.lowagie.text.pdf.draw.LineSeparator(1.2f, 40, GOLD, Element.ALIGN_CENTER, -4)));
        rule.setSpacingBefore(4);
        doc.add(rule);

        String sub = "Lập ngày " + r.generatedOn().format(DMY) + "  ·  " + r.memberCount() + " người  ·  "
                + (r.options().sort() == ExportOptions.Sort.BRANCH ? "Xếp theo nhánh" : "Xếp theo đời")
                + (r.options().publicView() ? "  ·  Bản công khai (đã che thông tin riêng tư)" : "  ·  Bản nội bộ");
        Paragraph s = new Paragraph(sub, new Font(regular, 9, Font.NORMAL, MUTED));
        s.setAlignment(Element.ALIGN_CENTER);
        s.setSpacingBefore(6);
        s.setSpacingAfter(12);
        doc.add(s);
    }

    private void writeTable(Document doc, ExportReport r) {
        List<ExportField> fields = r.options().orderedFields();
        float[] widths = new float[fields.size() + 1];
        widths[0] = 4.6f; // STT (du rong de chu "STT" khong bi ngat dong khi co nhieu cot)
        for (int i = 0; i < fields.size(); i++) widths[i + 1] = fields.get(i).pdfWidth;

        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1); // dong tieu de lap lai o moi trang

        Font head = new Font(bold, 9, Font.NORMAL, Color.WHITE);
        table.addCell(headerCell("STT", head));
        for (ExportField f : fields) table.addCell(headerCell(f.label, head));

        Font body = new Font(regular, 8.8f);
        Font bodyBold = new Font(bold, 8.8f);
        Font bodyItalic = new Font(italic, 8.8f, Font.NORMAL, MUTED);
        boolean byGeneration = r.options().sort() == ExportOptions.Sort.GENERATION;
        Integer currentGen = -1;
        int stripe = 0;

        for (ExportReport.Row row : r.rows()) {
            if (byGeneration && !java.util.Objects.equals(row.generation(), currentGen)) {
                currentGen = row.generation();
                PdfPCell g = new PdfPCell(new Phrase(currentGen == null ? "CHƯA RÕ ĐỜI" : "ĐỜI THỨ " + currentGen,
                        new Font(bold, 9.5f, Font.NORMAL, RED)));
                g.setColspan(fields.size() + 1);
                g.setBackgroundColor(GROUP);
                g.setBorderColor(GRID);
                g.setPadding(4);
                table.addCell(g);
            }
            if (!row.spouseRow()) stripe++;
            Color bg = stripe % 2 == 0 ? STRIPE : Color.WHITE;

            table.addCell(bodyCell(row.no() == null ? "" : String.valueOf(row.no()), body, bg, Element.ALIGN_CENTER, 0));
            for (ExportField f : fields) {
                String v = row.value(f);
                if (f == ExportField.FULL_NAME) {
                    // Thut le theo nhanh; nguoi huyet thong in dam, dau/re in nghieng ngay duoi
                    int indent = byGeneration ? 0 : Math.min(row.depth(), 10) * 7;
                    String text = row.spouseRow() ? "∞ " + v : v;
                    table.addCell(bodyCell(text, row.spouseRow() ? bodyItalic : bodyBold, bg, Element.ALIGN_LEFT, indent));
                } else {
                    boolean hidden = v.contains(ExportService.HIDDEN);
                    table.addCell(bodyCell(v.isEmpty() ? "—" : v, hidden ? bodyItalic : body, bg,
                            f == ExportField.GENERATION || f == ExportField.GENDER ? Element.ALIGN_CENTER : Element.ALIGN_LEFT, 0));
                }
            }
        }
        doc.add(table);
    }

    private PdfPCell headerCell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(RED);
        c.setBorderColor(RED);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPaddingTop(5);
        c.setPaddingBottom(6);
        return c;
    }

    private PdfPCell bodyCell(String text, Font font, Color bg, int align, float indent) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(bg);
        c.setBorderColor(GRID);
        c.setBorderWidth(0.5f);
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_TOP);
        c.setPadding(3.5f);
        c.setPaddingLeft(4 + indent);
        c.setLeading(0, 1.25f);
        return c;
    }

    private static List<String> nonEmpty(String... parts) {
        return java.util.Arrays.stream(parts).filter(StringUtils::hasText).toList();
    }

    /** Chan trang: ten dong ho ben trai, "Trang x / y" o giua, ngay lap ben phai */
    private final class Footer extends PdfPageEventHelper {
        private final ExportReport report;
        private PdfTemplate total;

        Footer(ExportReport report) {
            this.report = report;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(40, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Rectangle p = document.getPageSize();
            float y = 22;
            cb.setColorStroke(GRID);
            cb.setLineWidth(0.5f);
            cb.moveTo(document.left(), y + 12);
            cb.lineTo(document.right(), y + 12);
            cb.stroke();

            cb.beginText();
            cb.setFontAndSize(regular, 8);
            cb.setColorFill(MUTED);
            cb.showTextAligned(Element.ALIGN_LEFT, report.family().getName(), document.left(), y, 0);
            cb.showTextAligned(Element.ALIGN_RIGHT, "Gia Phả Việt · " + report.generatedOn().format(DMY), document.right(), y, 0);
            String label = "Trang " + writer.getPageNumber() + " / ";
            float w = regular.getWidthPoint(label, 8);
            float x = p.getWidth() / 2 - w / 2 - 6;
            cb.showTextAligned(Element.ALIGN_LEFT, label, x, y, 0);
            cb.endText();
            cb.addTemplate(total, x + w, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(regular, 8);
            total.setColorFill(MUTED);
            // Khi dong tai lieu, bo dem da tang sang trang ke tiep (chua ton tai) -> tru 1
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }
}
