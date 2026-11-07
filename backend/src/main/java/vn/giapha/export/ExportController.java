package vn.giapha.export;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Xuat bao cao gia pha. Quyen: Ban quan tri / Toc truong.
 * Vi du: GET /api/families/1/export/pdf?fields=FULL_NAME,GENERATION,BIRTH,DEATH&sort=BRANCH&paper=A4&landscape=true
 */
@RestController
@RequestMapping("/api/families/{familyId}/export")
@RequiredArgsConstructor
@Tag(name = "Xuất báo cáo", description = "Xuất gia phả ra PDF / Excel với các cột tự chọn")
@SecurityRequirement(name = "bearerAuth")
public class ExportController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExportService exportService;
    private final PdfExporter pdfExporter;
    private final ExcelExporter excelExporter;
    private final vn.giapha.export.tree.TreeChartService treeChartService;
    private final vn.giapha.export.tree.TreeChartPdf treeChartPdf;

    @GetMapping("/tree-pdf")
    @Operation(summary = "Xuất sơ đồ cây (PDF vector)",
            description = "Vẽ ở máy chủ: kết quả giống nhau trên mọi máy / trình duyệt, phóng to không vỡ, in được khổ A4 → A0.")
    public ResponseEntity<byte[]> treePdf(@PathVariable Long familyId,
                                          @RequestParam(defaultValue = "A3") vn.giapha.export.tree.TreeChartOptions.Paper paper,
                                          @RequestParam(defaultValue = "true") boolean landscape,
                                          @RequestParam(defaultValue = "false") boolean publicView,
                                          @Parameter(description = "Chỉ vẽ nhánh từ người này") @RequestParam(required = false) Long rootId,
                                          @Parameter(description = "Số đời tối đa tính từ gốc") @RequestParam(required = false) Integer maxDepth,
                                          @RequestParam(defaultValue = "true") boolean showDates,
                                          @RequestParam(defaultValue = "false") boolean inline) {
        var chart = treeChartService.build(familyId,
                vn.giapha.export.tree.TreeChartOptions.of(paper, landscape, publicView, rootId, maxDepth, showDates));
        return file(treeChartPdf.render(chart), MediaType.APPLICATION_PDF,
                fileName("SoDo", chart.family().getSurname(), chart.generatedOn(), "pdf"), inline);
    }

    @GetMapping("/pdf")
    @Operation(summary = "Xuất PDF", description = "inline=true để mở trực tiếp trong trình duyệt (xem / in), false để tải về")
    public ResponseEntity<byte[]> pdf(@PathVariable Long familyId,
                                      @Parameter(description = "Danh sách cột, phân cách dấu phẩy") @RequestParam(required = false) List<ExportField> fields,
                                      @RequestParam(defaultValue = "BRANCH") ExportOptions.Sort sort,
                                      @RequestParam(defaultValue = "A4") ExportOptions.Paper paper,
                                      @RequestParam(defaultValue = "false") boolean landscape,
                                      @Parameter(description = "Bản công khai: che thông tin như tài khoản Khách") @RequestParam(defaultValue = "false") boolean publicView,
                                      @RequestParam(defaultValue = "false") boolean inline) {
        ExportReport report = exportService.build(familyId, ExportOptions.of(fields, sort, paper, landscape, publicView));
        return file(pdfExporter.render(report), MediaType.APPLICATION_PDF, fileName(report, "pdf"), inline);
    }

    @GetMapping("/excel")
    @Operation(summary = "Xuất Excel (.xlsx)")
    public ResponseEntity<byte[]> excel(@PathVariable Long familyId,
                                        @RequestParam(required = false) List<ExportField> fields,
                                        @RequestParam(defaultValue = "BRANCH") ExportOptions.Sort sort,
                                        @RequestParam(defaultValue = "A4") ExportOptions.Paper paper,
                                        @RequestParam(defaultValue = "false") boolean publicView) {
        ExportReport report = exportService.build(familyId, ExportOptions.of(fields, sort, paper, true, publicView));
        return file(excelExporter.render(report), XLSX, fileName(report, "xlsx"), false);
    }

    private static ResponseEntity<byte[]> file(byte[] body, MediaType type, String name, boolean inline) {
        // Ten file da la ASCII -> filename="..." don gian (truyen charset se bi ma hoa =?UTF-8?Q?...?= khong can thiet)
        ContentDisposition cd = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(name).build();
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store") // du lieu ca nhan: khong luu cache
                .body(body);
    }

    private static String fileName(ExportReport r, String ext) {
        return fileName("GiaPha", r.family().getSurname(), r.generatedOn(), ext);
    }

    /** Vi du: GiaPha_Nguyen_20261106.pdf, SoDo_Trinh_20261107.pdf (khong dau de tuong thich moi he dieu hanh) */
    private static String fileName(String prefix, String familySurname, java.time.LocalDate date, String ext) {
        String surname = Normalizer.normalize(familySurname, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D').replaceAll("[^A-Za-z0-9]+", "");
        return prefix + "_" + (surname.isEmpty() ? "dongho" : surname) + "_"
                + date.format(DateTimeFormatter.BASIC_ISO_DATE) + "." + ext;
    }
}
