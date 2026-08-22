package com.app.shahbaztrades.components.analysis;

import com.app.shahbaztrades.components.yahoo.YahooClient;
import com.app.shahbaztrades.model.dto.analysis.TechnicalMetrics;
import com.app.shahbaztrades.util.TechnicalAnalysisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/** Derives the technical figures a strategy sizes its risk from. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TechnicalMetricsProvider {

    private final YahooClient yahooClient;

    /**
     * @return the symbol's ATR, or null when history is missing or the computed ATR fails its own
     * validity check — callers must treat a null as "trade without an ATR-based trail".
     */
    public TechnicalMetrics atrFor(String symbol) {
        var data = yahooClient.getMonthlyHistoricalData(symbol);
        if (CollectionUtils.isEmpty(data)) {
            return null;
        }

        var atr = TechnicalAnalysisUtil.getAtr(data);
        if (atr == null || !atr.isAtrValid()) {
            return null;
        }

        return atr;
    }
}
