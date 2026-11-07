package vn.giapha.lunar;

import java.time.LocalDate;

/**
 * Chuyen doi lich Am - Duong Viet Nam (mui gio GMT+7).
 * Port tu thuat toan cua Ho Ngoc Duc (https://www.informatik.uni-leipzig.de/~duc/amlich/).
 */
public final class LunarCalendar {

    private static final double TZ = 7.0;
    private static final String[] CAN = {"Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý"};
    private static final String[] CHI = {"Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi"};

    private LunarCalendar() {
    }

    // ---------- Public API ----------

    public static LunarDate solarToLunar(LocalDate date) {
        int dd = date.getDayOfMonth(), mm = date.getMonthValue(), yy = date.getYear();
        long dayNumber = jdFromDate(dd, mm, yy);
        // Mung 1 cua thang chua ngay nay = ky trang moi gan nhat KHONG MUON HON dayNumber.
        // Uoc luong k co the lech hon 1 chu ky o mot so ngay hiem (vd 07/05/2054 tung ra "ngay 0"),
        // nen dieu chinh bang vong lap ca hai chieu thay vi chi lui 1 buoc nhu thuat toan goc.
        int k = (int) Math.floor((dayNumber - 2415021.076998695) / 29.530588853) + 1;
        while (getNewMoonDay(k) > dayNumber) {
            k--;
        }
        while (getNewMoonDay(k + 1) <= dayNumber) {
            k++;
        }
        long monthStart = getNewMoonDay(k);
        long a11 = getLunarMonth11(yy);
        long b11 = a11;
        int lunarYear;
        if (a11 >= monthStart) {
            lunarYear = yy;
            a11 = getLunarMonth11(yy - 1);
        } else {
            lunarYear = yy + 1;
            b11 = getLunarMonth11(yy + 1);
        }
        int lunarDay = (int) (dayNumber - monthStart + 1);
        int diff = (int) Math.floor((monthStart - a11) / 29.0);
        boolean leap = false;
        int lunarMonth = diff + 11;
        if (b11 - a11 > 365) {
            int leapMonthDiff = getLeapMonthOffset(a11);
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10;
                if (diff == leapMonthDiff) {
                    leap = true;
                }
            }
        }
        if (lunarMonth > 12) {
            lunarMonth -= 12;
        }
        if (lunarMonth >= 11 && diff < 4) {
            lunarYear -= 1;
        }
        return new LunarDate(lunarDay, lunarMonth, lunarYear, leap);
    }

    /**
     * Doi ngay am sang duong. Tra ve null neu ngay am khong hop le (vi du thang nhuan khong ton tai).
     */
    public static LocalDate lunarToSolar(int lunarDay, int lunarMonth, int lunarYear, boolean leap) {
        long a11, b11;
        if (lunarMonth < 11) {
            a11 = getLunarMonth11(lunarYear - 1);
            b11 = getLunarMonth11(lunarYear);
        } else {
            a11 = getLunarMonth11(lunarYear);
            b11 = getLunarMonth11(lunarYear + 1);
        }
        int k = (int) Math.floor(0.5 + (a11 - 2415021.076998695) / 29.530588853);
        int off = lunarMonth - 11;
        if (off < 0) {
            off += 12;
        }
        if (b11 - a11 > 365) {
            int leapOff = getLeapMonthOffset(a11);
            int leapMonth = leapOff - 2;
            if (leapMonth < 0) {
                leapMonth += 12;
            }
            if (leap && lunarMonth != leapMonth) {
                return null;
            } else if (leap || off >= leapOff) {
                off += 1;
            }
        } else if (leap) {
            return null;
        }
        long monthStart = getNewMoonDay(k + off);
        return jdToDate(monthStart + lunarDay - 1);
    }

    /**
     * Doi ngay am (thang thuong) sang duong cho cac su kien lap lai hang nam (ngay gio).
     * Neu thang am nam do chi co 29 ngay ma ngay can tim la 30 thi lay ngay 29.
     */
    public static LocalDate lunarToSolarLenient(int lunarDay, int lunarMonth, int lunarYear) {
        LocalDate d = lunarToSolar(lunarDay, lunarMonth, lunarYear, false);
        if (d != null && lunarDay == 30) {
            LunarDate back = solarToLunar(d);
            if (back.day() != 30) {
                d = lunarToSolar(29, lunarMonth, lunarYear, false);
            }
        }
        return d;
    }

    public static String canChiYear(int lunarYear) {
        return CAN[Math.floorMod(lunarYear + 6, 10)] + " " + CHI[Math.floorMod(lunarYear + 8, 12)];
    }

    // ---------- Thuat toan ----------

    static long jdFromDate(int dd, int mm, int yy) {
        long a = (14 - mm) / 12;
        long y = yy + 4800 - a;
        long m = mm + 12 * a - 3;
        long jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
        }
        return jd;
    }

    static LocalDate jdToDate(long jd) {
        long a, b, c;
        if (jd > 2299160) {
            a = jd + 32044;
            b = (4 * a + 3) / 146097;
            c = a - b * 146097 / 4;
        } else {
            b = 0;
            c = jd + 32082;
        }
        long d = (4 * c + 3) / 1461;
        long e = c - 1461 * d / 4;
        long m = (5 * e + 2) / 153;
        int day = (int) (e - (153 * m + 2) / 5 + 1);
        int month = (int) (m + 3 - 12 * (m / 10));
        int year = (int) (b * 100 + d - 4800 + m / 10);
        return LocalDate.of(year, month, day);
    }

    private static double newMoon(int k) {
        double T = k / 1236.85;
        double T2 = T * T;
        double T3 = T2 * T;
        double dr = Math.PI / 180;
        double jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * T2 - 0.000000155 * T3;
        jd1 = jd1 + 0.00033 * Math.sin((166.56 + 132.87 * T - 0.009173 * T2) * dr);
        double M = 359.2242 + 29.10535608 * k - 0.0000333 * T2 - 0.00000347 * T3;
        double Mpr = 306.0253 + 385.81691806 * k + 0.0107306 * T2 + 0.00001236 * T3;
        double F = 21.2964 + 390.67050646 * k - 0.0016528 * T2 - 0.00000239 * T3;
        double C1 = (0.1734 - 0.000393 * T) * Math.sin(M * dr) + 0.0021 * Math.sin(2 * dr * M);
        C1 = C1 - 0.4068 * Math.sin(Mpr * dr) + 0.0161 * Math.sin(dr * 2 * Mpr);
        C1 = C1 - 0.0004 * Math.sin(dr * 3 * Mpr);
        C1 = C1 + 0.0104 * Math.sin(dr * 2 * F) - 0.0051 * Math.sin(dr * (M + Mpr));
        C1 = C1 - 0.0074 * Math.sin(dr * (M - Mpr)) + 0.0004 * Math.sin(dr * (2 * F + M));
        C1 = C1 - 0.0004 * Math.sin(dr * (2 * F - M)) - 0.0006 * Math.sin(dr * (2 * F + Mpr));
        C1 = C1 + 0.0010 * Math.sin(dr * (2 * F - Mpr)) + 0.0005 * Math.sin(dr * (2 * Mpr + M));
        double deltat;
        if (T < -11) {
            deltat = 0.001 + 0.000839 * T + 0.0002261 * T2 - 0.00000845 * T3 - 0.000000081 * T * T3;
        } else {
            deltat = -0.000278 + 0.000265 * T + 0.000262 * T2;
        }
        return jd1 + C1 - deltat;
    }

    private static double sunLongitude(double jdn) {
        double T = (jdn - 2451545.0) / 36525;
        double T2 = T * T;
        double dr = Math.PI / 180;
        double M = 357.52910 + 35999.05030 * T - 0.0001559 * T2 - 0.00000048 * T * T2;
        double L0 = 280.46645 + 36000.76983 * T + 0.0003032 * T2;
        double DL = (1.914600 - 0.004817 * T - 0.000014 * T2) * Math.sin(dr * M);
        DL = DL + (0.019993 - 0.000101 * T) * Math.sin(dr * 2 * M) + 0.000290 * Math.sin(dr * 3 * M);
        double L = (L0 + DL) * dr;
        L = L - Math.PI * 2 * Math.floor(L / (Math.PI * 2));
        return L;
    }

    private static long getNewMoonDay(int k) {
        return (long) Math.floor(newMoon(k) + 0.5 + TZ / 24);
    }

    private static int getSunLongitude(long dayNumber) {
        return (int) Math.floor(sunLongitude(dayNumber - 0.5 - TZ / 24) / Math.PI * 6);
    }

    private static long getLunarMonth11(int yy) {
        long off = jdFromDate(31, 12, yy) - 2415021;
        int k = (int) Math.floor(off / 29.530588853);
        long nm = getNewMoonDay(k);
        int sunLong = getSunLongitude(nm);
        if (sunLong >= 9) {
            nm = getNewMoonDay(k - 1);
        }
        return nm;
    }

    private static int getLeapMonthOffset(long a11) {
        int k = (int) Math.floor((a11 - 2415021.076998695) / 29.530588853 + 0.5);
        int last;
        int i = 1;
        int arc = getSunLongitude(getNewMoonDay(k + i));
        do {
            last = arc;
            i++;
            arc = getSunLongitude(getNewMoonDay(k + i));
        } while (arc != last && i < 14);
        return i - 1;
    }
}
