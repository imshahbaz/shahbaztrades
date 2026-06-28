package com.app.shahbaztrades.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@NoArgsConstructor(access = AccessLevel.PRIVATE)

public class DateUtil {

    public static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    public static final DateTimeFormatter NSE_INPUT_LAYOUT = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final int MARKET_CLOSING_HOUR = 15;
    private static final int MARKET_CLOSING_GRACE_MINUTE = 25;
    private static final int MARKET_SQUARE_OFF_MIN = 30;

    public static long zerodhaTokenExpiry() {
        ZonedDateTime now = ZonedDateTime.now(IST_ZONE);

        ZonedDateTime target = now.withHour(3).withMinute(0).withSecond(0).withNano(0);

        if (now.isAfter(target)) {
            target = target.plusDays(1);
        }

        return Duration.between(now, target).toSeconds();
    }

    public static LocalDate getTodayDate() {
        return LocalDate.now(IST_ZONE);
    }

    public static boolean isPastClosingGrace() {
        ZonedDateTime nowIst = ZonedDateTime.now(IST_ZONE);
        LocalTime currentTime = nowIst.toLocalTime();
        LocalTime graceTime = LocalTime.of(MARKET_CLOSING_HOUR, MARKET_CLOSING_GRACE_MINUTE);
        return !currentTime.isBefore(graceTime);
    }

    public static boolean isSquareOffTimeReached() {
        LocalTime now = ZonedDateTime.now(IST_ZONE).toLocalTime();
        LocalTime squareOffTime = LocalTime.of(MARKET_CLOSING_HOUR, MARKET_SQUARE_OFF_MIN);
        return !now.isBefore(squareOffTime);
    }

    public static boolean isMarketClosedForTrading() {
        ZonedDateTime nowInIndia = ZonedDateTime.now(IST_ZONE);
        DayOfWeek day = nowInIndia.getDayOfWeek();
        if (day.equals(DayOfWeek.SATURDAY) || day.equals(DayOfWeek.SUNDAY)) {
            return true;
        }

        LocalTime time = nowInIndia.toLocalTime();
        LocalTime marketOpenTime = LocalTime.of(9, 0);
        LocalTime marketCloseTime = LocalTime.of(MARKET_CLOSING_HOUR, MARKET_SQUARE_OFF_MIN);
        return time.isBefore(marketOpenTime) || time.isAfter(marketCloseTime);
    }

    public static LocalDateTime getCurrentDateTime() {
        return LocalDateTime.now(IST_ZONE);
    }

    public static Duration getDurationUntilMarketClose() {
        return Duration.between(LocalTime.now(IST_ZONE), LocalTime.of(MARKET_CLOSING_HOUR, MARKET_SQUARE_OFF_MIN));
    }

    public static Duration getDurationUntilMarketOpen(Duration defaultDuration) {
        var now = ZonedDateTime.now(IST_ZONE);
        LocalTime time = now.toLocalTime();

        LocalTime marketOpenTime = LocalTime.of(9, 15);
        LocalTime marketCloseTime = LocalTime.of(MARKET_CLOSING_HOUR, 45);
        DayOfWeek day = now.getDayOfWeek();

        if (isTradingDay(day) && !time.isBefore(marketOpenTime) && !time.isAfter(marketCloseTime)) {
            return defaultDuration;
        }

        if (isTradingDay(day) && time.isBefore(marketOpenTime)) {
            return Duration.between(time, marketOpenTime);
        }

        ZonedDateTime nextTargetOpen = calculateNextMarketOpen(now, marketOpenTime);
        return Duration.between(now, nextTargetOpen);
    }

    private static boolean isTradingDay(DayOfWeek day) {
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    private static ZonedDateTime calculateNextMarketOpen(ZonedDateTime now, LocalTime marketOpenTime) {
        ZonedDateTime target = now.plusDays(1)
                .with(marketOpenTime)
                .withSecond(0)
                .withNano(0);

        while (!isTradingDay(target.getDayOfWeek())) {
            target = target.plusDays(1);
        }

        return target;
    }

}