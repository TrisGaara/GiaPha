package vn.giapha.event;

import org.junit.jupiter.api.Test;
import vn.giapha.common.ApiException;
import vn.giapha.lunar.LunarCalendar;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class CalendarControllerTest {
    private final CalendarController controller = new CalendarController();

    @Test
    void leapYear2023HasThirteenMonthsWithLeapSecondMonth() {
        CalendarController.LunarYearInfo y = controller.lunarYear(2023);
        assertEquals("Quý Mão", y.yearName());
        assertEquals(2, y.leapMonth());
        assertEquals(13, y.months().size());
        assertEquals("1,2,2N,3,4,5,6,7,8,9,10,11,12",
                String.join(",", y.months().stream().map(m -> m.month() + (m.leap() ? "N" : "")).toList()));
        assertEquals(LocalDate.of(2023, 3, 22), y.months().get(2).startSolar(), "1/2 nhuận 2023 = 22/3/2023");
    }

    @Test
    void regularYear2026() {
        CalendarController.LunarYearInfo y = controller.lunarYear(2026);
        assertNull(y.leapMonth());
        assertEquals(12, y.months().size());
        assertEquals(LocalDate.of(2026, 2, 17), y.months().get(0).startSolar(), "Tết Bính Ngọ");
    }

    @Test
    void monthLengthsAreConsistentForManyYears() {
        for (int year = 1950; year <= 2100; year++) {
            CalendarController.LunarYearInfo y = controller.lunarYear(year);
            int total = 0;
            for (CalendarController.LunarMonthInfo m : y.months()) {
                assertTrue(m.days() == 29 || m.days() == 30, year + "/" + m.month() + ": " + m.days());
                // ngay cuoi thang doi ra am lich phai dung la ngay 29 / 30 cua thang do
                LocalDate lastDay = m.startSolar().plusDays(m.days() - 1);
                var last = LunarCalendar.solarToLunar(lastDay);
                assertEquals(m.days(), last.day(), "năm " + year + " tháng " + m.month() + (m.leap() ? "N" : "")
                        + " bắt đầu " + m.startSolar() + ", ngày cuối " + lastDay + " -> ÂL " + last);
                assertEquals(m.month(), last.month());
                assertEquals(m.leap(), last.leap());
                total += m.days();
            }
            long between = ChronoUnit.DAYS.between(LunarCalendar.lunarToSolar(1, 1, year, false),
                    LunarCalendar.lunarToSolar(1, 1, year + 1, false));
            assertEquals(between, total, "tổng số ngày năm " + year);
        }
    }

    @Test
    void rejectsOutOfRangeYear() {
        assertThrows(ApiException.class, () -> controller.lunarYear(1800));
    }
}
