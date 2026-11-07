package vn.giapha.export.tree;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import vn.giapha.export.PdfFonts;
import vn.giapha.family.Family;
import vn.giapha.family.FamilyRoleType;
import vn.giapha.family.PermissionService.Access;
import vn.giapha.marital.MaritalRelation;
import vn.giapha.marital.MarriageStatus;
import vn.giapha.member.FamilyMember;
import vn.giapha.member.Gender;
import vn.giapha.privacy.MaskedField;
import vn.giapha.privacy.ViewerContext;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TreeChartTest {
    private long ids = 1;

    // ---------- Bo cuc ----------

    @Test
    void parentsAreCenteredAndSiblingsDoNotOverlap() {
        Random rnd = new Random(42);
        for (int round = 0; round < 30; round++) { // nhieu cay ngau nhien
            ChartNode root = randomTree(rnd, 0);
            TreeChartLayout.Bounds b = TreeChartLayout.layout(List.of(root));
            List<ChartNode> all = new ArrayList<>();
            collect(root, all);
            Map<Double, List<ChartNode>> rows = new TreeMap<>();
            for (ChartNode n : all) rows.computeIfAbsent(n.y, k -> new ArrayList<>()).add(n);
            for (List<ChartNode> row : rows.values()) {
                row.sort(Comparator.comparingDouble(ChartNode::left));
                for (int i = 1; i < row.size(); i++) {
                    assertTrue(row.get(i).left() >= row.get(i - 1).left() + row.get(i - 1).width - 1e-6,
                            "các ô cùng đời không được chồng lên nhau");
                }
            }
            for (ChartNode n : all) {
                assertTrue(n.left() >= -1e-6 && n.left() + n.width <= b.width() + 1e-6, "ô nằm trong khung");
                if (!n.children.isEmpty()) {
                    double mid = (n.children.get(0).x + n.children.get(n.children.size() - 1).x) / 2;
                    // giua cac con, tru khi o cha me rong hon ca hang con
                    assertTrue(Math.abs(n.x - mid) < 1e-6 || n.width >= childrenSpan(n) - 1e-6, "cha mẹ nằm giữa các con");
                    for (int i = 1; i < n.children.size(); i++) assertTrue(n.children.get(i).x > n.children.get(i - 1).x, "giữ thứ tự con trái → phải");
                    for (ChartNode c : n.children) assertEquals(n.y + TreeChartLayout.CARD_HEIGHT + TreeChartLayout.V_GAP, c.y, 1e-6, "mỗi đời một hàng");
                }
            }
        }
    }

    @Test
    void cardWidthGrowsWithSpouses() {
        ChartNode n = node(Gender.MALE, "Chồng");
        n.partners.add(person(Gender.FEMALE, "Vợ cả", rel(1)));
        n.partners.add(person(Gender.FEMALE, "Vợ hai", rel(2)));
        TreeChartLayout.layout(List.of(n));
        assertEquals(3 * TreeChartLayout.PERSON_WIDTH, n.width, 1e-6);
        assertEquals(List.of("Chồng", "Vợ cả", "Vợ hai"), n.peopleLeftToRight().stream().map(p -> p.member().getFullName()).toList(),
                "chồng bên trái, các vợ bên phải");
        ChartNode daughter = node(Gender.FEMALE, "Con gái");
        daughter.partners.add(person(Gender.MALE, "Con rể", rel(1)));
        assertEquals(List.of("Con rể", "Con gái"), daughter.peopleLeftToRight().stream().map(p -> p.member().getFullName()).toList());
    }

    // ---------- Tien ich chu ----------

    @Test
    void wrapAndYears() {
        List<String> lines = TreeChartPdf.wrap("Nguyễn Đình Phúc Hậu Trường Thọ Vĩnh Viễn Bền Lâu", PdfFonts.BOLD, 8.6f, 92, 2);
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).endsWith("…"), "tên quá dài: cắt bớt có dấu …");
        lines.forEach(l -> assertTrue(PdfFonts.BOLD.getWidthPoint(l, 8.6f) <= 92.01f, "mỗi dòng vừa ô"));

        FamilyMember m = member(Gender.MALE, "A");
        m.setBirthLunarYear(1890);
        m.setAlive(false);
        m.setDeathDateSolar(LocalDate.of(1960, 4, 1));
        assertEquals("1890 – 1960", TreeChartPdf.years(m, EnumSet.noneOf(MaskedField.class)));
        assertEquals("? – 1960", TreeChartPdf.years(m, EnumSet.of(MaskedField.BIRTH_DATE)));
        assertEquals("Đã mất", TreeChartPdf.years(m, EnumSet.of(MaskedField.BIRTH_DATE, MaskedField.DEATH_DATE)));
        m.setAlive(true);
        assertEquals("s. 1890", TreeChartPdf.years(m, EnumSet.noneOf(MaskedField.class)));
    }

    // ---------- Ve PDF ----------

    @Test
    void rendersVectorPdfWithVietnameseTextOnChosenPaper() throws Exception {
        ChartNode root = node(Gender.MALE, "Trịnh Đắc Phạt");
        root.primary.member().setGeneration(1);
        root.primary.member().setAlive(false);
        root.primary.member().setDeathLunarDay(15);
        root.primary.member().setDeathLunarMonth(3);
        root.partners.add(person(Gender.FEMALE, "Trịnh Thị Kê", rel(1)));
        for (String n : List.of("Trịnh Đức Thưởng", "Trịnh Đức Huân", "Trịnh Đức Chung", "Trịnh Đức Thành")) {
            ChartNode c = node(Gender.MALE, n);
            c.partners.add(person(Gender.FEMALE, "Nguyễn Thị " + n.substring(n.lastIndexOf(' ') + 1), rel(1)));
            c.children.add(node(Gender.FEMALE, "Trịnh Thị Thu Hương " + n.substring(n.lastIndexOf(' ') + 1)));
            root.children.add(c);
        }
        root.children.get(1).hiddenDescendants = 3;
        Family f = new Family();
        f.setName("Họ Trịnh");
        f.setSurname("Trịnh");
        f.setOrigin("Kinh Dương, Hiệp Cát");
        ViewerContext admin = new ViewerContext(new Access(f, FamilyRoleType.OWNER, null, 1L, false), null, Set.of());

        for (TreeChartOptions.Paper paper : List.of(TreeChartOptions.Paper.A3, TreeChartOptions.Paper.A0)) {
            TreeChartOptions opt = TreeChartOptions.of(paper, true, false, null, null, true);
            TreeChartLayout.Bounds b = TreeChartLayout.layout(List.of(root));
            byte[] pdf = new TreeChartPdf().render(new TreeChartService.Chart(f, opt, List.of(root), b, admin, 14, LocalDate.of(2026, 11, 7)));
            PdfReader reader = new PdfReader(pdf);
            assertEquals(1, reader.getNumberOfPages(), "cả cây trên một trang");
            var size = reader.getPageSizeWithRotation(1);
            assertTrue(size.getWidth() > size.getHeight(), "giấy ngang");
            assertEquals(paper == TreeChartOptions.Paper.A3 ? 1190.55f : 3370.39f, size.getWidth(), 1f);
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            for (String s : List.of("GIA PHẢ", "Họ Trịnh", "Trịnh Đắc Phạt", "Trịnh Thị Kê", "Giỗ 15/3 ÂL", "Trịnh Đức Chung",
                    "+3 người đời sau", "Bản nội bộ")) {
                assertTrue(text.contains(s), paper + ": thiếu \"" + s + "\" trong PDF");
            }
        }
    }

    // ---------- helpers ----------

    private ChartNode randomTree(Random rnd, int depth) {
        ChartNode n = node(rnd.nextBoolean() ? Gender.MALE : Gender.FEMALE, "N" + ids);
        int spouses = rnd.nextInt(10) < 6 ? 1 : rnd.nextInt(3);
        for (int i = 0; i < spouses; i++) n.partners.add(person(Gender.FEMALE, "S" + ids, rel(i + 1)));
        int kids = depth >= 4 ? 0 : rnd.nextInt(depth == 0 ? 7 : 5);
        for (int i = 0; i < kids; i++) n.children.add(randomTree(rnd, depth + 1));
        return n;
    }

    private static double childrenSpan(ChartNode n) {
        ChartNode a = n.children.get(0), z = n.children.get(n.children.size() - 1);
        return z.left() + z.width - a.left();
    }

    private static void collect(ChartNode n, List<ChartNode> out) {
        out.add(n);
        n.children.forEach(c -> collect(c, out));
    }

    private ChartNode node(Gender g, String name) {
        return new ChartNode(new ChartNode.Person(member(g, name), null, false));
    }

    private ChartNode.Person person(Gender g, String name, MaritalRelation r) {
        FamilyMember m = member(g, name);
        m.setBloodLine(false);
        return new ChartNode.Person(m, r, false);
    }

    private FamilyMember member(Gender g, String name) {
        FamilyMember m = new FamilyMember();
        m.setId(ids++);
        m.setFullName(name);
        m.setGender(g);
        return m;
    }

    private static MaritalRelation rel(int order) {
        MaritalRelation r = new MaritalRelation();
        r.setMarriageOrder(order);
        r.setStatus(MarriageStatus.ACTIVE);
        return r;
    }
}
