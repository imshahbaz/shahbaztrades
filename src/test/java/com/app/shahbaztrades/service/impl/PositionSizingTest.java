package com.app.shahbaztrades.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks in the exact-decimal position-sizing that TradeEngineImpl.processTargetMargin relies on.
 * With binary floating point the affordable share count can drift by a whole share; BigDecimal
 * division with RoundingMode.DOWN is deterministic.
 */
class PositionSizingTest {

    private int shares(String budget, String marginPerShare) {
        return new BigDecimal(budget)
                .divide(new BigDecimal(marginPerShare), 0, RoundingMode.DOWN)
                .intValue();
    }

    @Test
    void bigDecimalSizing_isExactForTerminatingDecimals() {
        // 6 / 0.1 is exactly 60 with decimal arithmetic — no binary-float representation error.
        assertEquals(60, shares("6.0", "0.1"));
        assertEquals(100, shares("2500.00", "25.00"));
    }

    @Test
    void bigDecimalSizing_floorsPartialShares() {
        // 10000 / 333 = 30.03 -> 30 whole shares (never rounds up past the budget).
        assertEquals(30, shares("10000", "333"));
        assertEquals(3, shares("1000", "300"));
    }
}
