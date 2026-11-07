package vn.giapha.export;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.giapha.family.*;
import vn.giapha.family.PermissionService.Access;
import vn.giapha.marital.MaritalRelation;
import vn.giapha.marital.MaritalRelationRepository;
import vn.giapha.member.FamilyMember;
import vn.giapha.member.FamilyMemberRepository;
import vn.giapha.member.Gender;
import vn.giapha.privacy.PrivacyService;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExportTest {
    private final Family family = new Family();
    private final FamilyMemberRepository memberRepo = mock(FamilyMemberRepository.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private ExportService service;

    private FamilyMember to, ba, con, dau, chau, gai;

    @BeforeEach
    void setUp() {
        family.setId(1L);
        family.setName("Họ Nguyễn Đình làng Đông Ngạc");
        family.setSurname("Nguyễn");
        family.setOrigin("Hà Nội");
        to = m(1L, "Nguyễn Đình Tổ", Gender.MALE, true, null, null, 1, 1);
        to.setAlive(false);
        to.setDeathLunarDay(10);
        to.setDeathLunarMonth(3);
        ba = m(2L, "Trần Thị Bà", Gender.FEMALE, false, null, null, 1, 1);
        con = m(3L, "Nguyễn Đình Con", Gender.MALE, true, 1L, 2L, 2, 1);
        con.setPhone("0901 234 567");
        con.setVisibleOnTree(false);               // bi an khoi cay
        dau = m(4L, "Lê Thị Dâu", Gender.FEMALE, false, null, null, 2, 1);
        chau = m(5L, "Nguyễn Đình Cháu", Gender.MALE, true, 3L, 4L, 3, 1);
        gai = m(6L, "Nguyễn Thị Gái", Gender.FEMALE, true, 1L, 2L, 2, 2);
        gai.setBirthDateSolar(LocalDate.of(1950, 3, 3));
        gai.setHideBirthDate(true);

        when(memberRepo.findByFamilyIdOrderByGenerationAscBirthOrderAscIdAsc(1L)).thenReturn(List.of(to, ba, con, dau, gai, chau));
        when(memberRepo.findByFamilyIdAndUserId(anyLong(), anyLong())).thenReturn(List.of());
        MaritalRelationRepository relRepo = mock(MaritalRelationRepository.class);
        when(relRepo.findByFamilyId(1L)).thenReturn(List.of(rel(1, 1L, 2L), rel(2, 3L, 4L)));
        FamilyRoleRepository roleRepo = mock(FamilyRoleRepository.class);
        when(roleRepo.findByFamilyId(1L)).thenReturn(List.of());

        when(permissions.require(1L, FamilyRoleType.ADMIN)).thenReturn(new Access(family, FamilyRoleType.OWNER, null, 99L, false));
        when(permissions.editableScope(any())).thenAnswer(i -> ((Access) i.getArgument(0)).atLeast(FamilyRoleType.ADMIN) ? null : Set.of());

        service = new ExportService(permissions, new PrivacyService(memberRepo, permissions), memberRepo, relRepo, roleRepo);
        ReflectionTestUtils.setField(service, "timezone", "Asia/Ho_Chi_Minh");
    }

    private static final EnumSet<ExportField> ALL = EnumSet.allOf(ExportField.class);

    @Test
    void internalReportFollowsGenealogyOrder() {
        ExportReport r = service.build(1L, ExportOptions.of(ALL, ExportOptions.Sort.BRANCH, null, false, false));
        assertEquals(List.of("Nguyễn Đình Tổ", "Trần Thị Bà", "Nguyễn Đình Con", "Lê Thị Dâu", "Nguyễn Đình Cháu", "Nguyễn Thị Gái"),
                names(r), "phả ký: người → vợ/chồng → các con (theo thứ tự sinh)");
        assertEquals(Arrays.asList(1, null, 2, null, 3, 4), r.rows().stream().map(ExportReport.Row::no).toList(), "STT chỉ đánh cho người huyết thống");
        assertEquals(List.of(0, 0, 1, 1, 2, 1), r.rows().stream().map(ExportReport.Row::depth).toList());
        ExportReport.Row toRow = r.rows().get(0);
        assertEquals("Thủy tổ", toRow.value(ExportField.ROLE));
        assertTrue(toRow.value(ExportField.DEATH).contains("Giỗ 10/3 ÂL"));
        assertEquals("Vợ cả (Chính thất) của Nguyễn Đình Tổ", r.rows().get(1).value(ExportField.ROLE));
        assertEquals("Con trai – Con cả · Trưởng nam", r.rows().get(2).value(ExportField.ROLE));
        assertEquals("0901 234 567", r.rows().get(2).value(ExportField.CONTACT), "bản nội bộ: thấy liên hệ");
        assertTrue(r.rows().get(5).value(ExportField.BIRTH).startsWith("03/03/1950"));
        assertEquals("Cha: Nguyễn Đình Con\nMẹ: Lê Thị Dâu", r.rows().get(4).value(ExportField.PARENTS));
    }

    @Test
    void publicReportAppliesGuestPrivacy() {
        ExportReport r = service.build(1L, ExportOptions.of(ALL, ExportOptions.Sort.BRANCH, null, false, true));
        List<String> n = names(r);
        assertFalse(n.contains("Nguyễn Đình Con"), "người bị ẩn khỏi cây không có trong bản công khai");
        assertTrue(n.contains("Nguyễn Đình Cháu"), "con cháu vẫn có");
        String all = r.rows().toString();
        assertFalse(all.contains("Nguyễn Đình Con"), "không lộ tên người bị ẩn ở bất kỳ cột nào (cha mẹ, vợ chồng, vai trò)");
        assertFalse(all.contains("0901 234 567"));
        ExportReport.Row dauRow = r.rows().stream().filter(x -> x.value(ExportField.FULL_NAME).equals("Lê Thị Dâu")).findFirst().orElseThrow();
        assertEquals("Con dâu", dauRow.value(ExportField.ROLE));
        ExportReport.Row gaiRow = r.rows().stream().filter(x -> x.value(ExportField.FULL_NAME).equals("Nguyễn Thị Gái")).findFirst().orElseThrow();
        assertEquals(ExportService.HIDDEN, gaiRow.value(ExportField.BIRTH));
    }

    @Test
    void generationSortAndDefaultFields() {
        ExportReport r = service.build(1L, ExportOptions.of(null, ExportOptions.Sort.GENERATION, null, false, false));
        List<Integer> gens = r.rows().stream().map(ExportReport.Row::generation).toList();
        assertEquals(gens.stream().sorted().toList(), gens);
        assertEquals(ExportOptions.DEFAULT_FIELDS, r.options().fields());
        assertTrue(ExportOptions.of(EnumSet.of(ExportField.NOTE), null, null, false, false).fields().contains(ExportField.FULL_NAME),
                "họ tên luôn có");
    }

    @Test
    void pdfHasVietnameseTextHeaderAndPageTotals() throws Exception {
        List<ExportReport.Row> rows = new ArrayList<>();
        for (int i = 1; i <= 120; i++) {
            rows.add(new ExportReport.Row(i, i % 4, false, 1 + i / 40, Map.of(ExportField.FULL_NAME, "Nguyễn Đình Thế Hệ " + i,
                    ExportField.DEATH, "Giỗ 10/3 ÂL", ExportField.CONTACT, ExportService.HIDDEN)));
        }
        ExportReport report = new ExportReport(family, ExportOptions.of(EnumSet.of(ExportField.FULL_NAME, ExportField.DEATH, ExportField.CONTACT),
                ExportOptions.Sort.GENERATION, ExportOptions.Paper.A4, false, true), rows, 120, LocalDate.of(2026, 11, 6));
        byte[] pdf = new PdfExporter().render(report);
        assertEquals("%PDF", new String(pdf, 0, 4));

        PdfReader reader = new PdfReader(pdf);
        int pages = reader.getNumberOfPages();
        assertTrue(pages >= 3, "120 dòng phải sang nhiều trang");
        PdfTextExtractor ex = new PdfTextExtractor(reader);
        String first = ex.getTextFromPage(1);
        String last = ex.getTextFromPage(pages);
        assertTrue(first.contains("GIA PHẢ") && first.contains("Họ Nguyễn Đình làng Đông Ngạc"), first);
        assertTrue(first.contains("Nguyễn Đình Thế Hệ 1") && first.contains("Giỗ 10/3 ÂL") && first.contains("ĐỜI THỨ 1"));
        assertTrue(first.contains("Bản công khai"));
        int at = first.indexOf("Trang");
        assertTrue(first.contains("Trang 1 / " + pages), "chân trang có tổng số trang (" + pages + " trang): "
                + (at < 0 ? "KHÔNG THẤY 'Trang'" : first.substring(at, Math.min(first.length(), at + 30))));
        assertTrue(last.contains("Trang " + pages + " / " + pages));
        assertTrue(ex.getTextFromPage(2).contains("Họ và tên"), "dòng tiêu đề lặp lại ở trang sau");

        Rectangle2Check.assertA3Landscape(new PdfExporter(), report);
    }

    @Test
    void excelHasHeaderRowsAndIndentation() throws Exception {
        ExportReport r = service.build(1L, ExportOptions.of(EnumSet.of(ExportField.FULL_NAME, ExportField.GENERATION, ExportField.ROLE),
                ExportOptions.Sort.BRANCH, null, false, false));
        byte[] xlsx = new ExcelExporter().render(r);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet s = wb.getSheetAt(0);
            assertTrue(s.getRow(0).getCell(0).getStringCellValue().contains("Họ Nguyễn Đình làng Đông Ngạc"));
            assertEquals(List.of("STT", "Họ và tên", "Đời", "Vai trò / Quan hệ"),
                    List.of(0, 1, 2, 3).stream().map(i -> s.getRow(3).getCell(i).getStringCellValue()).toList());
            assertEquals(6, s.getLastRowNum() - 3, "6 dòng dữ liệu");
            assertEquals("Nguyễn Đình Cháu", s.getRow(8).getCell(1).getStringCellValue());
            assertEquals(2, s.getRow(8).getCell(1).getCellStyle().getIndention(), "thụt lề theo nhánh");
            assertEquals(3.0, s.getRow(8).getCell(2).getNumericCellValue(), "cột Đời là số để lọc/sắp xếp");
            assertNotNull(s.getPaneInformation(), "cố định dòng tiêu đề");
        }
    }

    /** Kho giay & huong giay */
    static final class Rectangle2Check {
        static void assertA3Landscape(PdfExporter exporter, ExportReport base) throws Exception {
            ExportReport a3 = new ExportReport(base.family(), ExportOptions.of(base.options().fields(), base.options().sort(),
                    ExportOptions.Paper.A3, true, false), base.rows(), base.memberCount(), base.generatedOn());
            com.lowagie.text.Rectangle size = new PdfReader(exporter.render(a3)).getPageSizeWithRotation(1);
            assertEquals(1190.55f, size.getWidth(), 1f, "A3 ngang rộng 420mm");
            assertEquals(841.89f, size.getHeight(), 1f);
        }
    }

    // ---------- helpers ----------

    private static List<String> names(ExportReport r) {
        return r.rows().stream().map(x -> x.value(ExportField.FULL_NAME)).toList();
    }

    private static FamilyMember m(Long id, String name, Gender g, boolean blood, Long father, Long mother, int gen, int order) {
        FamilyMember x = new FamilyMember();
        x.setId(id);
        x.setFamilyId(1L);
        x.setFullName(name);
        x.setGender(g);
        x.setBloodLine(blood);
        x.setFatherId(father);
        x.setMotherId(mother);
        x.setGeneration(gen);
        x.setBirthOrder(order);
        x.setHideContactInfo(true);
        return x;
    }

    private static MaritalRelation rel(long id, Long husband, Long wife) {
        MaritalRelation r = new MaritalRelation();
        r.setId(id);
        r.setFamilyId(1L);
        r.setHusbandId(husband);
        r.setWifeId(wife);
        r.setMarriageOrder(1);
        return r;
    }
}
