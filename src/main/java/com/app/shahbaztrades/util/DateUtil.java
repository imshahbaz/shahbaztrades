package com.app.shahbaztrades.util;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtil {

    public static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    public static final DateTimeFormatter chartInkFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

}