package com.app.shahbaztrades.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.concurrent.TimeUnit;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ThreadUtil {

    /**
     * Sleeps, restoring the interrupt flag rather than swallowing it.
     *
     * @return false if the wait was cut short by an interrupt, so callers can abandon their loop.
     */
    public static boolean pollWait(long waitMillis) {
        try {
            TimeUnit.MILLISECONDS.sleep(waitMillis);
            return true;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
