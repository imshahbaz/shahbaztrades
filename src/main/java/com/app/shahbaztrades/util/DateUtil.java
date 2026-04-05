package com.app.shahbaztrades.util;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class DateUtil {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    public static long zerodhaTokenExpiry() {
        ZonedDateTime now = ZonedDateTime.now(IST_ZONE);

        // Set target to today at 3:00 AM IST
        ZonedDateTime target = now.withHour(3).withMinute(0).withSecond(0).withNano(0);

        // If it's already past 3:00 AM today, set target to tomorrow 3:00 AM
        if (now.isAfter(target)) {
            target = target.plusDays(1);
        }

        return Duration.between(now, target).toSeconds();
    }

}