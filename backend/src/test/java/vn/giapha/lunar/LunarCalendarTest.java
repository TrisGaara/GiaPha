package vn.giapha.lunar;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class LunarCalendarTest {

    @Test
    void tetNguyenDan() {
        assertEquals(new LunarDate(1, 1, 2026, false), LunarCalendar.solarToLunar(LocalDate.of(2026, 2, 17)));
        assertEquals(new LunarDate(1, 1, 2025, false), LunarCalendar.solarToLunar(LocalDate.of(2025, 1, 29)));
        assertEquals(new LunarDate(1, 1, 2024, false), LunarCalendar.solarToLunar(LocalDate.of(2024, 2, 10)));
        assertEquals(LocalDate.of(2026, 2, 17), LunarCalendar.lunarToSolar(1, 1, 2026, false));
    }

    @Test
    void leapMonth2023() {
        // Nam Quy Mao 2023 nhuan thang 2
        assertEquals(new LunarDate(1, 2, 2023, true), LunarCalendar.solarToLunar(LocalDate.of(2023, 3, 22)));
        assertEquals(LocalDate.of(2023, 3, 22), LunarCalendar.lunarToSolar(1, 2, 2023, true));
        assertNull(LunarCalendar.lunarToSolar(1, 3, 2023, true));
    }

    @Test
    void canChi() {
        assertEquals("Bính Ngọ", LunarCalendar.canChiYear(2026));
        assertEquals("Giáp Thìn", LunarCalendar.canChiYear(2024));
    }

    @Test
    void roundTripEveryDay1950To2100() {
        LocalDate d = LocalDate.of(1950, 1, 1);
        LocalDate end = LocalDate.of(2100, 12, 31);
        while (!d.isAfter(end)) {
            LunarDate l = LunarCalendar.solarToLunar(d);
            assertEquals(d, LunarCalendar.lunarToSolar(l.day(), l.month(), l.year(), l.leap()), "at " + d);
            d = d.plusDays(1);
        }
    }

    @Test
    void everyDayHasValidLunarDay1900To2199() {
        // Round-trip khong bat duoc loi "ngay 0" (ngay 0 thang 4 doi nguoc van ra dung ngay duong) -> kiem tra truc tiep
        LocalDate d = LocalDate.of(1900, 1, 31);
        LocalDate end = LocalDate.of(2199, 12, 31);
        LunarDate prev = LunarCalendar.solarToLunar(d.minusDays(1));
        while (!d.isAfter(end)) {
            LunarDate l = LunarCalendar.solarToLunar(d);
            assertTrue(l.day() >= 1 && l.day() <= 30, d + " -> " + l);
            assertTrue(l.month() >= 1 && l.month() <= 12, d + " -> " + l);
            // ngay am tang dan 1, hoac quay ve mung 1 sau ngay 29/30
            assertTrue(l.day() == prev.day() + 1 || (l.day() == 1 && prev.day() >= 29), d + ": " + prev + " -> " + l);
            prev = l;
            d = d.plusDays(1);
        }
        assertEquals(new LunarDate(30, 3, 2054, false), LunarCalendar.solarToLunar(LocalDate.of(2054, 5, 7)), "ca từng lỗi");
    }

    @Test
    void lenientDay30() {
        LocalDate d = LunarCalendar.lunarToSolarLenient(30, 12, 2025);
        assertNotNull(d);
        LunarDate back = LunarCalendar.solarToLunar(d);
        assertEquals(12, back.month());
        assertTrue(back.day() == 30 || back.day() == 29);
    }
}
