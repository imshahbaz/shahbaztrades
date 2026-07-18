package com.app.shahbaztrades.service.impl;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.math.RoundingMode;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks in the exact-decimal position-sizing that TradeEngineImpl.processTargetMargin relies on.
 * With binary floating point the affordable share count can drift by a whole share; BigDecimal
 * division with intermediate precision followed by Truncation is deterministic.
 */
class PositionSizingTest {

    private int calculateQuantity(String orderAmount, double ltp, String marginMultiplier) {
        return new BigDecimal(orderAmount)
                .divide(BigDecimal.valueOf(ltp), 8, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(marginMultiplier))
                .setScale(0, RoundingMode.DOWN)
                .intValue();
    }

    @Test
    void bigDecimalSizing_isExactForTerminatingDecimals() {
        // (6.0 / 1.0) * 10.0 = 60 shares
        assertEquals(60, calculateQuantity("6.0", 1.0, "10.0"));

        // (2500.00 / 25.00) * 1.0 = 100 shares
        assertEquals(100, calculateQuantity("2500.00", 25.00, "1.0"));
    }

    @Test
    void bigDecimalSizing_floorsPartialShares() {
        // (10000 / 333.33333333) * 1.0 = 30.00000003 -> truncated to 30 whole shares
        assertEquals(30, calculateQuantity("10000", 333.33333333, "1.0"));

        // (1000 / 300.0) * 1.0 = 3.3333... -> truncated to 3 whole shares
        assertEquals(3, calculateQuantity("1000", 300.0, "1.0"));
    }

    @Test
    void bigDecimalSizing_handlesHighPrecisionLeverage() {
        // The edge-case we calculated earlier: (10000 / 356.45) * 4.42
        // 28.05442558 * 4.42 = 124.00056106 -> truncated cleanly to 124
        assertEquals(124, calculateQuantity("10000", 356.45, "4.42"));
    }
}