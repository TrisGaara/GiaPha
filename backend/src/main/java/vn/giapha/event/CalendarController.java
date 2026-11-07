package vn.giapha.event;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.common.ApiException;
import vn.giapha.lunar.LunarCalendar;
import vn.giapha.lunar.LunarDate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** API chuyen doi lich Am - Duong (cong khai) */
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    public record LunarView(int day, int month, int year, boolean leap, String yearName, String label) {
        static LunarView of(LunarDate l) {
            return new LunarView(l.day(), l.month(), l.year(), l.leap(), l.yearName(), l.label());
        }
    }

    public record DayView(LocalDate solar, LunarView lunar) {
    }

    @GetMapping("/to-lunar")
    public DayView toLunar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return new DayView(date, LunarView.of(LunarCalendar.solarToLunar(date)));
    }

    @GetMapping("/to-solar")
    public DayView toSolar(@RequestParam int day, @RequestParam int month, @RequestParam int year,
                           @RequestParam(defaultValue = "false") boolean leap) {
        if (day < 1 || day > 30 || month < 1 || month > 12) {
            throw ApiException.badRequest("Ngày/tháng âm lịch không hợp lệ");
        }
        LocalDate d = LunarCalendar.lunarToSolar(day, month, year, leap);
        if (d == null) {
            throw ApiException.badRequest("Năm " + year + " không có tháng " + month + " nhuận");
        }
        return new DayView(d, LunarView.of(LunarCalendar.solarToLunar(d)));
    }

    @GetMapping("/month")
    public List<DayView> month(@RequestParam int year, @RequestParam int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<DayView> days = new ArrayList<>();
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate date = ym.atDay(d);
            days.add(new DayView(date, LunarView.of(LunarCalendar.solarToLunar(date))));
        }
        return days;
    }

    /** Mot thang am lich: thu tu, co nhuan khong, so ngay (29 thieu / 30 du), ngay duong mung 1 */
    public record LunarMonthInfo(int month, boolean leap, int days, LocalDate startSolar) {
    }

    /** Cau truc mot nam am lich (12 hoac 13 thang) - dung cho bo chon ngay am o Frontend */
    public record LunarYearInfo(int year, String yearName, Integer leapMonth, List<LunarMonthInfo> months) {
    }

    /**
     * Cac thang cua nam am lich {@code year}, theo dung thu tu (thang nhuan dung ngay sau thang thuong cung so).
     * Vi du 2023: ..., 2, 2 (nhuan), 3, ... ; so ngay moi thang = mung 1 thang sau - mung 1 thang nay.
     */
    @GetMapping("/lunar-year")
    public LunarYearInfo lunarYear(@RequestParam int year) {
        if (year < 1900 || year > 2199) {
            throw ApiException.badRequest("Năm âm lịch phải trong khoảng 1900 – 2199");
        }
        List<LocalDate[]> starts = new ArrayList<>(); // [ngay bat dau], kem thong tin thang
        List<int[]> keys = new ArrayList<>();          // {thang, nhuan 0/1}
        Integer leapMonth = null;
        for (int m = 1; m <= 12; m++) {
            starts.add(new LocalDate[]{LunarCalendar.lunarToSolar(1, m, year, false)});
            keys.add(new int[]{m, 0});
            LocalDate leap = LunarCalendar.lunarToSolar(1, m, year, true);
            if (leap != null) {
                leapMonth = m;
                starts.add(new LocalDate[]{leap});
                keys.add(new int[]{m, 1});
            }
        }
        LocalDate nextYear = LunarCalendar.lunarToSolar(1, 1, year + 1, false);
        List<LunarMonthInfo> months = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            LocalDate start = starts.get(i)[0];
            LocalDate end = i + 1 < starts.size() ? starts.get(i + 1)[0] : nextYear;
            months.add(new LunarMonthInfo(keys.get(i)[0], keys.get(i)[1] == 1,
                    (int) java.time.temporal.ChronoUnit.DAYS.between(start, end), start));
        }
        return new LunarYearInfo(year, LunarCalendar.canChiYear(year), leapMonth, months);
    }
}
