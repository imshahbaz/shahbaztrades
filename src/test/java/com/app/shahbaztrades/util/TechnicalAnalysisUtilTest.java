package com.app.shahbaztrades.util;

import com.app.shahbaztrades.model.dto.nse.NSEHistoricalData;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechnicalAnalysisUtilTest {

    /** Builds n daily candles with a constant true range of {@code range} around {@code base}. */
    private List<NSEHistoricalData> series(int n, double base, double range) {
        List<NSEHistoricalData> data = new ArrayList<>(n);
        LocalDate day = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < n; i++) {
            data.add(NSEHistoricalData.builder()
                    .symbol("TEST")
                    .open(base)
                    .high(base + range / 2)
                    .low(base - range / 2)
                    .close(base)
                    .timestamp(DateUtil.NSE_INPUT_LAYOUT.format(day.plusDays(i)))
                    .build());
        }
        return data;
    }

    @Test
    void getAtr_convergesOnTheConstantTrueRange() {
        // Every bar has high-low = 10 and no gaps, so the ATR must settle on 10.
        var metrics = TechnicalAnalysisUtil.getAtr(series(60, 100.0, 10.0));

        assertEquals(10.0, metrics.getAtrValue(), 0.01);
    }

    @Test
    void getAtr_expressesExpectedMoveAsAPercentOfTheLatestClose() {
        // ATR 10 on a close of 100 is a 10% expected move.
        var metrics = TechnicalAnalysisUtil.getAtr(series(60, 100.0, 10.0));

        assertEquals(10.0, metrics.getExpectedMovePercent(), 0.01);
    }

    @Test
    void getAtr_roundsBothFieldsToTwoDecimals() {
        var metrics = TechnicalAnalysisUtil.getAtr(series(60, 137.77, 3.33));

        assertEquals(metrics.getAtrValue(), Math.round(metrics.getAtrValue() * 100) / 100.0, 1e-9);
        assertEquals(metrics.getExpectedMovePercent(),
                Math.round(metrics.getExpectedMovePercent() * 100) / 100.0, 1e-9);
    }

    @Test
    void getAtr_producesValidMetricsForRealisticData() {
        var metrics = TechnicalAnalysisUtil.getAtr(series(30, 500.0, 8.0));

        // isAtrValid gates whether AbstractDailyTradingStrategy trails on ATR at all.
        assertTrue(metrics.isAtrValid());
    }

    @Test
    void isAtrValid_rejectsZeroOrNegativeMetrics() {
        assertFalse(com.app.shahbaztrades.model.dto.analysis.TechnicalMetrics.builder()
                .atrValue(0).expectedMovePercent(1).build().isAtrValid());
        assertFalse(com.app.shahbaztrades.model.dto.analysis.TechnicalMetrics.builder()
                .atrValue(1).expectedMovePercent(0).build().isAtrValid());
        assertTrue(com.app.shahbaztrades.model.dto.analysis.TechnicalMetrics.builder()
                .atrValue(1).expectedMovePercent(1).build().isAtrValid());
    }
}
