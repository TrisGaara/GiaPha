package vn.giapha.export.tree;

import vn.giapha.marital.MaritalRelation;
import vn.giapha.member.FamilyMember;

import java.util.ArrayList;
import java.util.List;

/**
 * Mot o tren so do = mot thanh vien huyet thong (primary) + cac doi vo/chong cua ho (partners), giong CoupleCard o Frontend.
 * Toa do (x = tam ngang, y = canh tren) tinh boi {@link TreeChartLayout}, don vi diem PDF (1/72 inch) truoc khi co gian.
 */
public final class ChartNode {
    /** Mot nguoi trong o */
    public record Person(FamilyMember member, MaritalRelation relation, boolean placeholder) {
    }

    final Person primary;
    final List<Person> partners = new ArrayList<>();
    final List<ChartNode> children = new ArrayList<>();
    /** So con chau khong ve vi vuot qua so doi gioi han */
    int hiddenDescendants;
    /** Thu tu doi vo/chong cua nguoi cha/me ngoai toc (khi cha/me co tu 2 doi) - ve huy hieu so */
    Integer parentOrder;

    // ---- Bo cuc ----
    double x;
    double y;
    double width;
    double height;

    ChartNode(Person primary) {
        this.primary = primary;
    }

    /** Thu tu ve tren o: chong ben trai, vo ben phai (giong giao dien web) */
    List<Person> peopleLeftToRight() {
        List<Person> all = new ArrayList<>();
        boolean primaryIsHusband = primary.member().getGender() == vn.giapha.member.Gender.MALE;
        if (primaryIsHusband) {
            all.add(primary);
            all.addAll(partners);
        } else {
            all.addAll(partners);
            all.add(primary);
        }
        return all;
    }

    double left() {
        return x - width / 2;
    }

    double bottom() {
        return y + height;
    }

    public List<ChartNode> children() {
        return children;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double width() {
        return width;
    }

    public Person primary() {
        return primary;
    }
}
