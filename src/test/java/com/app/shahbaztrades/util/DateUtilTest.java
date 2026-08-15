package com.app.shahbaztrades.util;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DateUtil reads the wall clock, so these assert the invariants that must hold at any instant
 * rather than fixed values.
 */
class DateUtilTest {

    @Test
    void getTodayDate_isTodayInIst() {
        assertEquals(LocalDate.now(DateUtil.IST_ZONE), DateUtil.getTodayDate());
    }

    @Test
    void zerodhaTokenExpiry_alwaysPointsAtTheNext3amIst() {
        long seconds = DateUtil.zerodhaTokenExpiry();

        // The broker token dies at 03:00 IST, so the TTL is always in (0, 24h].
        assertTrue(seconds > 0, "expiry must be in the future");
        assertTrue(seconds <= Duration.ofDays(1).toSeconds(), "expiry must never exceed one day");
    }

    @Test
    void isPastClosingGrace_matchesThe1514IstBoundary() {
        LocalTime now = ZonedDateTime.now(DateUtil.IST_ZONE).toLocalTime();
        assertEquals(!now.isBefore(LocalTime.of(15, 14)), DateUtil.isPastClosingGrace());
    }

    @Test
    void isSquareOffTimeReached_matchesThe1530IstBoundary() {
        LocalTime now = ZonedDateTime.now(DateUtil.IST_ZONE).toLocalTime();
        assertEquals(!now.isBefore(LocalTime.of(15, 30)), DateUtil.isSquareOffTimeReached());
    }

    @Test
    void isSquareOffTimeReached_impliesPastClosingGrace() {
        // 15:30 is after 15:14, so square-off can never be reached while the grace window is open.
        if (DateUtil.isSquareOffTimeReached()) {
            assertTrue(DateUtil.isPastClosingGrace());
        }
    }

    @Test
    void isMarketClosedForTrading_isAlwaysTrueOnWeekends() {
        DayOfWeek today = ZonedDateTime.now(DateUtil.IST_ZONE).getDayOfWeek();
        if (today == DayOfWeek.SATURDAY || today == DayOfWeek.SUNDAY) {
            assertTrue(DateUtil.isMarketClosedForTrading());
        }
    }

    @Test
    void getDurationUntilMarketOpen_returnsDefaultWhileTheMarketIsOpen() {
        Duration fallback = Duration.ofMinutes(10);
        Duration result = DateUtil.getDurationUntilMarketOpen(fallback);

        var now = ZonedDateTime.now(DateUtil.IST_ZONE);
        boolean tradingDay = now.getDayOfWeek() != DayOfWeek.SATURDAY && now.getDayOfWeek() != DayOfWeek.SUNDAY;
        LocalTime time = now.toLocalTime();
        boolean inSession = tradingDay
                && !time.isBefore(LocalTime.of(9, 15))
                && !time.isAfter(LocalTime.of(15, 45));

        if (inSession) {
            assertEquals(fallback, result, "cache TTLs must not be stretched during live trading");
        }
    }

    @Test
    void getDurationUntilMarketOpen_neverReturnsNegativeOrAbsurdlyLongTtl() {
        Duration result = DateUtil.getDurationUntilMarketOpen(Duration.ofMinutes(10));

        assertFalse(result.isNegative(), "a negative TTL would evict cache entries immediately");
        // Worst case is Friday evening -> Monday 09:15, i.e. under four days.
        assertTrue(result.compareTo(Duration.ofDays(4)) < 0);
    }

    @Test
    void getCurrentDateTime_isOnTheSameIstDayAsGetTodayDate() {
        assertEquals(DateUtil.getTodayDate(), DateUtil.getCurrentDateTime().toLocalDate());
    }

    @Test
    void nseInputLayout_formatsDayMonthYear() {
        assertEquals("15-Aug-2026", DateUtil.NSE_INPUT_LAYOUT.format(LocalDate.of(2026, 8, 15)));
    }
}
