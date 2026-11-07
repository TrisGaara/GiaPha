package vn.giapha.export;

import com.lowagie.text.pdf.BaseFont;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Font Noto Serif (SIL OFL) nhung vao PDF - dung chung cho bao cao danh sach va so do cay.
 * Font chuan cua PDF khong co dau tieng Viet nen bat buoc phai nhung font.
 * Nap mot lan cho ca ung dung.
 */
public final class PdfFonts {
    public static final BaseFont REGULAR = load("fonts/NotoSerif-Regular.ttf");
    public static final BaseFont BOLD = load("fonts/NotoSerif-Bold.ttf");
    public static final BaseFont ITALIC = load("fonts/NotoSerif-Italic.ttf");

    private PdfFonts() {
    }

    private static BaseFont load(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            // IDENTITY_H + nhung tap con (chi ky tu duoc dung) -> dung moi dau tieng Viet, file gon
            return BaseFont.createFont(path.substring(path.lastIndexOf('/') + 1), BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        } catch (IOException e) {
            throw new UncheckedIOException("Không nạp được font " + path, e);
        }
    }
}
