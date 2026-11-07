package vn.giapha.export.tree;

import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;

/**
 * Tuy chon xuat so do cay.
 *
 * @param paper      kho giay (A4 -> A0; so do la vector nen in kho lon van net)
 * @param landscape  giay ngang
 * @param publicView ap dung quyen rieng tu nhu tai khoan Khach
 * @param rootId     chi ve nhanh tu nguoi nay (null = ca dong ho)
 * @param maxDepth   so doi toi da tinh tu goc
 * @param showDates  hien nam sinh / mat va ngay gio tren o
 */
public record TreeChartOptions(Paper paper, boolean landscape, boolean publicView, Long rootId, int maxDepth, boolean showDates) {

    public enum Paper {
        A4(PageSize.A4), A3(PageSize.A3), A2(PageSize.A2), A1(PageSize.A1), A0(PageSize.A0);

        final Rectangle size;

        Paper(Rectangle size) {
            this.size = size;
        }
    }

    public static TreeChartOptions of(Paper paper, Boolean landscape, boolean publicView, Long rootId, Integer maxDepth, Boolean showDates) {
        return new TreeChartOptions(paper == null ? Paper.A3 : paper, landscape == null || landscape, publicView, rootId,
                maxDepth == null ? 50 : Math.max(1, Math.min(maxDepth, 50)), showDates == null || showDates);
    }

    Rectangle pageSize() {
        return landscape ? paper.size.rotate() : paper.size;
    }
}
