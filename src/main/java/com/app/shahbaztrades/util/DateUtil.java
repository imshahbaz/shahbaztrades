package com.app.shahbaztrades.util;

import java.time.*;
import java.time.format.DateTimeFormatter;

public class DateUtil {

    public static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    public static final DateTimeFormatter chartInkFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
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

    public static Duration getNseCacheExpiryTime() {
        ZonedDateTime now = ZonedDateTime.now(IST_ZONE);

        ZonedDateTime start = now.withHour(8).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime end = now.withHour(17).withMinute(30).withSecond(0).withNano(0);

        if (now.isAfter(start) && now.isBefore(end)) {
            return Duration.ofMinutes(10);
        }

        if (now.isBefore(start)) {
            return Duration.between(now, start);
        }

        return Duration.between(now, start.plusDays(1));
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
        LocalTime now = ZonedDateTime.now(IST_ZONE).toLocalTime();
        LocalTime marketCloseTime = LocalTime.of(MARKET_CLOSING_HOUR, MARKET_CLOSING_HOUR);
        return !now.isBefore(marketCloseTime);
    }

    public static LocalDateTime getCurrentDateTime() {
        return LocalDateTime.now(IST_ZONE);
    }

}