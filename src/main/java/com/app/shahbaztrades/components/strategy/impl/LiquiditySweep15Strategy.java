package com.app.shahbaztrades.components.strategy.impl;

import com.app.shahbaztrades.components.strategy.AbstractContinuousTradingStrategy;
import com.app.shahbaztrades.service.MarginService;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

@Component
public class LiquiditySweep15Strategy extends AbstractContinuousTradingStrategy {

    public LiquiditySweep15Strategy(MarginService marginService) {
        super(marginService);
    }

    @Override
    public String getName() {
        return "LIQUIDITY SWEEP";
    }

    @Override
    public String watchlistKey() {
        return "MACD15MINLOCAL";
    }

    @Override
    protected boolean matches(BarSeries series) {
        int safeClosedIndex = lastClosedIndex(series);
        int availableBars = safeClosedIndex - series.getBeginIndex() + 1;
        if (availableBars < 4) return false;

        return applyEntryRule(series, safeClosedIndex);
    }

    private boolean applyEntryRule(BarSeries series, int safeClosedIndex) {
        if (!isBullishClose(series, safeClosedIndex)) {
            return false;
        }

        Num lowestOfPastThreeBars = lowestLow(series, safeClosedIndex - 3, safeClosedIndex - 1);
        var bar = series.getBar(safeClosedIndex);
        return bar.getLowPrice().isLessThan(lowestOfPastThreeBars) && bar.getClosePrice().isGreaterThan(lowestOfPastThreeBars);
    }

    private Num lowestLow(BarSeries series, int fromIndex, int toIndex) {
        Num lowest = series.getBar(fromIndex).getLowPrice();
        for (int i = fromIndex + 1; i <= toIndex; i++) {
            Num low = series.getBar(i).getLowPrice();
            if (low.isLessThan(lowest)) {
                lowest = low;
            }
        }
        return lowest;
    }

    private boolean isBullishClose(BarSeries series, int safeClosedIndex) {
        var bar = series.getBar(safeClosedIndex);
        if (bar.getOpenPrice().isGreaterThanOrEqual(bar.getClosePrice())) {
            return false;
        }

        var high = bar.getHighPrice();
        var low = bar.getLowPrice();
        var open = bar.getOpenPrice();
        var range = high.minus(low);
        if (range.isLessThanOrEqual(series.numFactory().zero())) {
            return false;
        }

        var threshold = series.numFactory().numOf(0.4);
        return open.minus(low).dividedBy(range).isGreaterThan(threshold);
    }
}
