package vn.giapha.export.tree;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.giapha.export.PdfFonts;
import vn.giapha.marital.MaritalRelation;
import vn.giapha.marital.MarriageStatus;
import vn.giapha.member.FamilyMember;
import vn.giapha.member.Gender;
import vn.giapha.privacy.MaskedField;
import vn.giapha.privacy.ViewerContext;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static vn.giapha.export.tree.TreeChartLayout.*;

/**
 * Ve so do cay gia pha thanh PDF VECTOR (OpenPDF) - khong phu thuoc trinh duyet:
 * cung du lieu -> cung ket qua tren moi may; phong to bao nhieu chu van net; in duoc kho lon A2 / A1 / A0.
 * <p>
 * He toa do: bo cuc tinh theo (x sang phai, y di xuong); khi ve dung CTM = [scale, 0, 0, scale, ox, oyTop]
 * va doi dau y -> moi khung, chu, duong noi co gian dong bo.
 */
@Component
public class TreeChartPdf {
    private static final Color RED = new Color(0x7c, 0x23, 0x23);
    private static final Color GOLD = new Color(0xb7, 0x79, 0x1f);
    private static final Color LINE = new Color(0xc9, 0xa9, 0x78);
    private static final Color DIVIDER = new Color(0xea, 0xdf, 0xcd);
    private static final Color MALE = new Color(0x2b, 0x6c, 0xb0);
    private static final Color FEMALE = new Color(0xb8, 0x32, 0x80);
    private static final Color DEAD_BG = new Color(0xf3, 0xf4, 0xf6);
    private static final Color DEAD = new Color(0x9c, 0xa3, 0xaf);
    private static final Color TEXT = new Color(0x2d, 0x2a, 0x26);
    private static final Color MUTED = new Color(0x6b, 0x62, 0x5a);
    private static final Color HEART = new Color(0xc5, 0x30, 0x30);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float MARGIN = 28;

    private final BaseFont regular = PdfFonts.REGULAR;
    private final BaseFont bold = PdfFonts.BOLD;
    private final BaseFont italic = PdfFonts.ITALIC;

    public byte[] render(TreeChartService.Chart chart) {
        Rectangle page = chart.options().pageSize();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(page, 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        doc.addTitle("Sơ đồ gia phả " + chart.family().getName());
        doc.addCreator("Gia Phả Việt");
        doc.open();
        PdfContentByte cb = writer.getDirectContent();

        // Tieu de / chan trang co gian theo kho giay (A0 lon hon A4 ~4 lan)
        float f = Math.max(1f, Math.max(page.getWidth(), page.getHeight()) / 842f);
        float headerH = drawHeader(cb, chart, page, f);
        float footerH = 20 * f;

        double availW = page.getWidth() - 2 * MARGIN * f;
        double availH = page.getHeight() - 2 * MARGIN * f - headerH - footerH;
        TreeChartLayout.Bounds b = chart.bounds();
        double scale = 1;
        if (b.width() > 0 && b.height() > 0) {
            scale = Math.min(Math.min(availW / b.width(), availH / b.height()), 1.6 * f);
            double ox = MARGIN * f + (availW - b.width() * scale) / 2;
            double oyTop = page.getHeight() - MARGIN * f - headerH;
            cb.saveState();
            cb.concatCTM((float) scale, 0, 0, (float) scale, (float) ox, (float) oyTop);
            for (ChartNode r : chart.roots()) drawLinks(cb, r);
            for (ChartNode r : chart.roots()) drawCards(cb, r, chart.viewer(), chart.options().showDates());
            cb.restoreState();
        } else {
            text(cb, regular, 12 * f, MUTED, Element.ALIGN_CENTER, "Phả hệ chưa có dữ liệu", page.getWidth() / 2, page.getHeight() / 2);
        }
        drawFooter(cb, chart, page, f, scale);
        doc.close();
        return out.toByteArray();
    }

    // =====================================================================
    // Tieu de & chan trang
    // =====================================================================

    private float drawHeader(PdfContentByte cb, TreeChartService.Chart chart, Rectangle page, float f) {
        float cx = page.getWidth() / 2;
        float top = page.getHeight() - MARGIN * f;
        text(cb, bold, 22 * f, RED, Element.ALIGN_CENTER, "GIA PHẢ", cx, top - 20 * f);
        text(cb, bold, 13 * f, GOLD, Element.ALIGN_CENTER, chart.family().getName(), cx, top - 38 * f);
        List<String> meta = new ArrayList<>();
        meta.add("Họ " + chart.family().getSurname());
        if (StringUtils.hasText(chart.family().getOrigin())) meta.add("Nguyên quán: " + chart.family().getOrigin());
        if (StringUtils.hasText(chart.family().getAncestralHallAddress())) meta.add("Nhà thờ tổ: " + chart.family().getAncestralHallAddress());
        text(cb, italic, 8.5f * f, MUTED, Element.ALIGN_CENTER, String.join("  ·  ", meta), cx, top - 51 * f);
        cb.setColorStroke(GOLD);
        cb.setLineWidth(0.8f * f);
        cb.moveTo(cx - 60 * f, top - 58 * f);
        cb.lineTo(cx + 60 * f, top - 58 * f);
        cb.stroke();
        return 72 * f;
    }

    private void drawFooter(PdfContentByte cb, TreeChartService.Chart chart, Rectangle page, float f, double scale) {
        float y = MARGIN * f * 0.6f;
        float size = 7.5f * f;
        cb.setColorStroke(DIVIDER);
        cb.setLineWidth(0.5f * f);
        cb.moveTo(MARGIN * f, y + 11 * f);
        cb.lineTo(page.getWidth() - MARGIN * f, y + 11 * f);
        cb.stroke();
        text(cb, regular, size, MUTED, Element.ALIGN_LEFT,
                // Font Noto Serif khong co ky tu so tron (①②) -> dien dat bang chu
                "Viền trên xanh: nam · hồng: nữ · nền xám: đã mất · nét đứt: dâu/rể · số trong vòng tròn: đời vợ/chồng",
                MARGIN * f, y);
        String right = "Lập ngày " + chart.generatedOn().format(DMY) + " · " + chart.people() + " người · "
                + (chart.options().publicView() ? "Bản công khai" : "Bản nội bộ");
        text(cb, regular, size, MUTED, Element.ALIGN_RIGHT, right, page.getWidth() - MARGIN * f, y);
        if (scale < 0.55) {
            text(cb, italic, size, RED, Element.ALIGN_CENTER, "Sơ đồ đã thu nhỏ còn " + Math.round(scale * 100)
                    + "% để vừa trang – chọn khổ giấy lớn hơn (A2 / A1 / A0) để chữ rõ hơn", page.getWidth() / 2, y);
        }
    }

    // =====================================================================
    // Duong noi: day o cha me -> thanh ngang chung -> dinh o con
    // =====================================================================

    private void drawLinks(PdfContentByte cb, ChartNode n) {
        cb.setColorStroke(LINE);
        cb.setLineWidth(1.2f);
        for (ChartNode c : n.children) {
            double mid = c.y - V_GAP / 2;
            cb.moveTo((float) n.x, (float) -n.bottom());
            cb.lineTo((float) n.x, (float) -mid);
            cb.lineTo((float) c.x, (float) -mid);
            cb.lineTo((float) c.x, (float) -c.y);
            cb.stroke();
            drawLinks(cb, c);
        }
    }

    // =====================================================================
    // O cap vo chong
    // =====================================================================

    private void drawCards(PdfContentByte cb, ChartNode n, ViewerContext viewer, boolean showDates) {
        float left = (float) n.left();
        float top = (float) n.y;
        float w = (float) n.width;
        float h = (float) n.height;

        // Khung chung
        cb.setColorFill(Color.WHITE);
        cb.setColorStroke(LINE);
        cb.setLineWidth(1f);
        cb.roundRectangle(left, -(top + h), w, h, 6);
        cb.fillStroke();

        List<ChartNode.Person> people = n.peopleLeftToRight();
        for (int i = 0; i < people.size(); i++) {
            float px = left + i * (float) PERSON_WIDTH;
            ChartNode.Person p = people.get(i);
            drawPerson(cb, p, px, top, h, viewer, showDates, p != n.primary && n.partners.size() > 1);
            if (i > 0) {
                // Vach ngan + trang thai hon nhan giua hai nguoi
                MaritalRelation rel = (p != n.primary ? p : people.get(i - 1)).relation();
                drawDivider(cb, px, top, h, rel);
            }
        }
        if (n.parentOrder != null) badge(cb, left + w - 9, top, n.parentOrder, FEMALE);
        if (n.hiddenDescendants > 0) {
            text(cb, italic, 6.5f, MUTED, Element.ALIGN_CENTER, "+" + n.hiddenDescendants + " người đời sau (ngoài phạm vi in)",
                    (float) n.x, -(top + h + 9));
        }
        for (ChartNode c : n.children) drawCards(cb, c, viewer, showDates);
    }

    private void drawPerson(PdfContentByte cb, ChartNode.Person p, float px, float top, float h, ViewerContext viewer,
                            boolean showDates, boolean showOrder) {
        FamilyMember m = p.member();
        float pw = (float) PERSON_WIDTH;
        float cx = px + pw / 2;

        if (p.placeholder()) {
            cb.setColorFill(DEAD_BG);
            cb.rectangle(px + 2, -(top + h - 2), pw - 4, h - 4);
            cb.fill();
            text(cb, italic, 8f, MUTED, Element.ALIGN_CENTER, "Thành viên ẩn", cx, -(top + 26));
            return;
        }
        if (!m.isAlive()) {
            cb.setColorFill(DEAD_BG);
            cb.rectangle(px + 1.5f, -(top + h - 1.5f), pw - 3, h - 4);
            cb.fill();
        }
        // Vach mau tren dinh: xanh = nam, hong = nu, xam = da mat; net dut = dau / re
        cb.setColorStroke(!m.isAlive() ? DEAD : m.getGender() == Gender.MALE ? MALE : FEMALE);
        cb.setLineWidth(2.4f);
        if (!m.isBloodLine()) cb.setLineDash(3f, 2f, 0f);
        cb.moveTo(px + 6, -(top + 2.2f));
        cb.lineTo(px + pw - 6, -(top + 2.2f));
        cb.stroke();
        cb.setLineDash(0f);

        // Huy hieu doi
        if (m.getGeneration() != null) {
            String g = "Đ" + m.getGeneration();
            float gw = bold.getWidthPoint(g, 6.2f) + 6;
            cb.setColorFill(GOLD);
            cb.roundRectangle(px + 5, -(top + 4.5f), gw, 9, 3);
            cb.fill();
            text(cb, bold, 6.2f, Color.WHITE, Element.ALIGN_CENTER, g, px + 5 + gw / 2, -(top + 2.3f));
        }
        if (showOrder && p.relation() != null) badge(cb, px + pw - 10, top + 9, p.relation().getMarriageOrder(), FEMALE);

        // Chu: ten (toi da 2 dong), nam sinh - mat, ngay gio
        Color nameColor = m.isAlive() ? TEXT : MUTED;
        float y = top + 17;
        for (String line : wrap(m.getFullName(), bold, 8.6f, pw - 12, 2)) {
            text(cb, bold, 8.6f, nameColor, Element.ALIGN_CENTER, line, cx, -y);
            y += 10.5f;
        }
        if (showDates) {
            Set<MaskedField> mask = viewer.maskedFields(m);
            String years = years(m, mask);
            if (!years.isEmpty()) {
                text(cb, regular, 7.4f, MUTED, Element.ALIGN_CENTER, years, cx, -(y + 0.5f));
                y += 9.5f;
            }
            if (!m.isAlive() && !mask.contains(MaskedField.DEATH_DATE)
                    && m.getDeathLunarDay() != null && m.getDeathLunarMonth() != null) {
                text(cb, regular, 7f, RED, Element.ALIGN_CENTER,
                        "Giỗ " + m.getDeathLunarDay() + "/" + m.getDeathLunarMonth() + " ÂL", cx, -(y + 0.5f));
            }
        }
    }

    private void drawDivider(PdfContentByte cb, float x, float top, float h, MaritalRelation rel) {
        cb.setColorStroke(DIVIDER);
        cb.setLineWidth(0.8f);
        cb.moveTo(x, -(top + 8));
        cb.lineTo(x, -(top + h - 8));
        cb.stroke();
        float cy = -(top + 27);
        cb.setColorFill(Color.WHITE);
        cb.setColorStroke(LINE);
        cb.setLineWidth(0.8f);
        cb.circle(x, cy, 5.5f);
        cb.fillStroke();
        MarriageStatus status = rel == null ? MarriageStatus.ACTIVE : rel.getStatus();
        if (status == MarriageStatus.DIVORCED) {
            // Ly hon: dau x xam
            cb.setColorStroke(DEAD);
            cb.setLineWidth(1f);
            cb.moveTo(x - 2.5f, cy - 2.5f);
            cb.lineTo(x + 2.5f, cy + 2.5f);
            cb.moveTo(x - 2.5f, cy + 2.5f);
            cb.lineTo(x + 2.5f, cy - 2.5f);
            cb.stroke();
        } else {
            // Dang chung song: cham do; goa: cham xam
            cb.setColorFill(status == MarriageStatus.DECEASED ? DEAD : HEART);
            cb.circle(x, cy, 2.6f);
            cb.fill();
        }
    }

    /** Huy hieu so tron (thu tu doi vo / chong) */
    private void badge(PdfContentByte cb, float x, float top, int number, Color color) {
        cb.setColorFill(Color.WHITE);
        cb.setColorStroke(color);
        cb.setLineWidth(0.8f);
        cb.circle(x, -top, 5);
        cb.fillStroke();
        text(cb, bold, 6f, color, Element.ALIGN_CENTER, String.valueOf(number), x, -top - 2.1f);
    }

    // =====================================================================
    // Tien ich
    // =====================================================================

    /** "1890 – 1960", "s. 1920"; ton trong quyen rieng tu (an nam sinh / nam mat) */
    static String years(FamilyMember m, Set<MaskedField> mask) {
        // Luu y: Integer.valueOf - neu tron int va Integer trong toan tu ?: Java se unbox -> NPE khi nam am lich null
        String by = mask.contains(MaskedField.BIRTH_DATE) ? null
                : year(m.getBirthDateSolar() != null ? Integer.valueOf(m.getBirthDateSolar().getYear()) : m.getBirthLunarYear());
        if (m.isAlive()) return by == null ? "" : "s. " + by;
        String dy = mask.contains(MaskedField.DEATH_DATE) ? null
                : year(m.getDeathDateSolar() != null ? Integer.valueOf(m.getDeathDateSolar().getYear()) : m.getDeathLunarYear());
        if (by == null && dy == null) return "Đã mất";
        return (by == null ? "?" : by) + " – " + (dy == null ? "?" : dy);
    }

    private static String year(Integer y) {
        return y == null ? null : String.valueOf(y);
    }

    /** Ngat dong theo be rong (toi da maxLines dong, dong cuoi them "…" neu con thua) */
    static List<String> wrap(String text, BaseFont font, float size, float maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        String[] words = text.trim().split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String candidate = cur.isEmpty() ? words[i] : cur + " " + words[i];
            if (font.getWidthPoint(candidate, size) <= maxWidth || cur.isEmpty()) {
                cur.setLength(0);
                cur.append(candidate);
            } else {
                lines.add(cur.toString());
                cur.setLength(0);
                cur.append(words[i]);
                if (lines.size() == maxLines - 1) {
                    // dong cuoi: gom phan con lai, cat bot neu qua dai
                    String rest = String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length));
                    lines.add(ellipsis(rest, font, size, maxWidth));
                    return lines;
                }
            }
        }
        if (!cur.isEmpty()) lines.add(ellipsis(cur.toString(), font, size, maxWidth));
        return lines;
    }

    private static String ellipsis(String s, BaseFont font, float size, float maxWidth) {
        if (font.getWidthPoint(s, size) <= maxWidth) return s;
        String t = s;
        while (t.length() > 1 && font.getWidthPoint(t + "…", size) > maxWidth) t = t.substring(0, t.length() - 1);
        return t.trim() + "…";
    }

    private static void text(PdfContentByte cb, BaseFont font, float size, Color color, int align, String s, float x, float y) {
        cb.beginText();
        cb.setFontAndSize(font, size);
        cb.setColorFill(color);
        cb.showTextAligned(align, s, x, y, 0);
        cb.endText();
    }
}
