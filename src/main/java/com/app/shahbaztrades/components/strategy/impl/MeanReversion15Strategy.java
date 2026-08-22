package com.app.shahbaztrades.components.strategy.impl;

import com.app.shahbaztrades.components.strategy.AbstractContinuousTradingStrategy;
import com.app.shahbaztrades.service.MarginService;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

@Component
public class MeanReversion15Strategy extends AbstractContinuousTradingStrategy {

    public MeanReversion15Strategy(MarginService marginService) {
        super(marginService);
    }

    @Override
    public String getName() {
        return "MEAN REVERSION";
    }

    @Override
    public String watchlistKey() {
        return "MACD15MINLOCAL";
    }

    @Override
    protected boolean matches(BarSeries series) {
        int safeClosedIndex = lastClosedIndex(series);
        int availableBars = safeClosedIndex - series.getBeginIndex() + 1;
        if (availableBars < 3) return false;

        return applyEntryRule(series, safeClosedIndex);
    }

    private boolean applyEntryRule(BarSeries series, int safeClosedIndex) {
        Bar currentBar = series.getBar(safeClosedIndex);
        Bar previousBar = series.getBar(safeClosedIndex - 1);

        if (previousBar.getLowPrice().isGreaterThanOrEqual(currentBar.getLowPrice())) {
            return false;
        }

        Bar previousPreviousBar = series.getBar(safeClosedIndex - 2);

        if (previousBar.getLowPrice().isGreaterThanOrEqual(previousPreviousBar.getLowPrice())) {
            return false;
        }

        if (previousBar.getHighPrice().isGreaterThanOrEqual(currentBar.getClosePrice())) {
            return false;
        }

        return isBullishClose(series, safeClosedIndex);
    }

    private boolean isBullishClose(BarSeries series, int safeClosedIndex) {
        var bar = series.getBar(safeClosedIndex - 1);
        var high = bar.getHighPrice();
        var low = bar.getLowPrice();
        var close = bar.getClosePrice();
        var range = high.minus(low);
        if (range.isLessThanOrEqual(series.numFactory().zero())) {
            return false;
        }

        var threshold = series.numFactory().numOf(0.6);
        return close.minus(low).dividedBy(range).isGreaterThan(threshold);
    }

}
