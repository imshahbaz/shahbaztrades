package com.app.shahbaztrades.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Exchange price mechanics. Static because the tick bands are set by the exchange, not by us. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PriceUtil {

    /**
     * Rounds to the nearest valid tick for the price band. A price off-tick is rejected by the
     * broker outright, so every computed price must pass through here before it is sent.
     */
    public static double fixToTick(double price) {
        BigDecimal tick;

        if (price < 250) {
            tick = new BigDecimal("0.01");
        } else if (price <= 1000) {
            tick = new BigDecimal("0.05");
        } else if (price <= 5000) {
            tick = new BigDecimal("0.10");
        } else if (price <= 10000) {
            tick = new BigDecimal("0.50");
        } else if (price <= 20000) {
            tick = BigDecimal.ONE;
        } else {
            tick = new BigDecimal("5.00");
        }

        BigDecimal exact = BigDecimal.valueOf(price);
        return exact.divide(tick, 0, RoundingMode.HALF_UP).multiply(tick).doubleValue();
    }
}
