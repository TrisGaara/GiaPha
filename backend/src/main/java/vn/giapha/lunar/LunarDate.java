package vn.giapha.lunar;

public record LunarDate(int day, int month, int year, boolean leap) {

    public String yearName() {
        return LunarCalendar.canChiYear(year);
    }

    /** Vi du: "15/7 (nhuận) năm Bính Ngọ" */
    public String label() {
        return day + "/" + month + (leap ? " (nhuận)" : "") + " năm " + yearName();
    }
}
