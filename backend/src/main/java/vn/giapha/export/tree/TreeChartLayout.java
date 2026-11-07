package vn.giapha.export.tree;

import java.util.List;

/**
 * Bo cuc "tidy tree" cho so do gia pha (thuat toan thuan, khong phu thuoc thu vien ve):
 * <ul>
 *     <li>Moi doi mot hang, cach deu nhau theo chieu doc.</li>
 *     <li>Be rong cay con = max(be rong o, tong be rong cac cay con + khoang cach).</li>
 *     <li>O cha me nam chinh giua o con dau va o con cuoi -> duong noi can doi, cac nhanh khong de len nhau.</li>
 * </ul>
 * Ket qua: gan x (tam), y (dinh), width, height cho moi {@link ChartNode}.
 */
public final class TreeChartLayout {
    /** Be rong mot nguoi trong o (pt) */
    public static final double PERSON_WIDTH = 104;
    /** Chieu cao o (pt) */
    public static final double CARD_HEIGHT = 62;
    /** Khoang cach ngang giua cac cay con anh chi em (pt) */
    public static final double H_GAP = 14;
    /** Khoang cach ngang giua cac cay goc (pt) */
    public static final double ROOT_GAP = 36;
    /** Khoang cach doc giua cac doi (pt) - cho cho duong noi */
    public static final double V_GAP = 42;

    private TreeChartLayout() {
    }

    /** Kich thuoc toan bo so do sau khi bo cuc */
    public record Bounds(double width, double height) {
    }

    public static Bounds layout(List<ChartNode> roots) {
        double cursor = 0;
        double maxBottom = 0;
        for (int i = 0; i < roots.size(); i++) {
            ChartNode r = roots.get(i);
            measure(r);
            double w = place(r, cursor, 0);
            cursor += w + (i < roots.size() - 1 ? ROOT_GAP : 0);
            maxBottom = Math.max(maxBottom, deepestBottom(r));
        }
        return new Bounds(cursor, maxBottom);
    }

    /** Kich thuoc tung o (so nguoi trong o) */
    private static void measure(ChartNode n) {
        n.width = PERSON_WIDTH * (1 + n.partners.size());
        n.height = CARD_HEIGHT;
        n.children.forEach(TreeChartLayout::measure);
    }

    /**
     * Dat cay con bat dau tu mep trai {@code left} o do sau {@code depth}.
     *
     * @return be rong cay con
     */
    private static double place(ChartNode n, double left, int depth) {
        n.y = depth * (CARD_HEIGHT + V_GAP);
        if (n.children.isEmpty()) {
            n.x = left + n.width / 2;
            return n.width;
        }
        // Be rong hang con (do truoc de can giua khi o cha me rong hon hang con)
        double childrenWidth = 0;
        for (ChartNode c : n.children) childrenWidth += subtreeWidth(c);
        childrenWidth += H_GAP * (n.children.size() - 1);
        double total = Math.max(n.width, childrenWidth);

        double start = left + (total - childrenWidth) / 2;
        for (ChartNode c : n.children) {
            start += place(c, start, depth + 1) + H_GAP;
        }
        // Cha me o chinh giua con dau va con cuoi, nhung khong vuot ra ngoai pham vi cay con
        double mid = (n.children.get(0).x + n.children.get(n.children.size() - 1).x) / 2;
        n.x = Math.min(Math.max(mid, left + n.width / 2), left + total - n.width / 2);
        return total;
    }

    private static double subtreeWidth(ChartNode n) {
        if (n.children.isEmpty()) return n.width;
        double w = 0;
        for (ChartNode c : n.children) w += subtreeWidth(c);
        w += H_GAP * (n.children.size() - 1);
        return Math.max(n.width, w);
    }

    private static double deepestBottom(ChartNode n) {
        double b = n.bottom();
        for (ChartNode c : n.children) b = Math.max(b, deepestBottom(c));
        return b;
    }
}
