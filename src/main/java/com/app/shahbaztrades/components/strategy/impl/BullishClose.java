package com.app.shahbaztrades.components.strategy.impl;

import com.app.shahbaztrades.components.strategy.AbstractContinuousTradingStrategy;
import com.app.shahbaztrades.service.MarginService;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;

@Component
public class BullishClose extends AbstractContinuousTradingStrategy {

    protected BullishClose(MarginService marginService) {
        super(marginService);
    }

    @Override
    public String getName() {
        return "BULLISH CLOSE";
    }

    @Override
    public String watchlistKey() {
        return "MACD15MINLOCAL";
    }

    @Override
    protected boolean matches(BarSeries series) {
        int safeClosedIndex = lastClosedIndex(series);
        int availableBars = safeClosedIndex - series.getBeginIndex() + 1;
        if (availableBars < 2) return false;
        return applyEntryRule(series, safeClosedIndex);
    }

    private boolean applyEntryRule(BarSeries series, int safeClosedIndex) {
        var child = series.getBar(safeClosedIndex);
        if (child.getClosePrice().isLessThanOrEqual(child.getOpenPrice()))
            return false;
        var mother = series.getBar(safeClosedIndex - 1);
        return child.getClosePrice().isGreaterThan(mother.getHighPrice());
    }

}
