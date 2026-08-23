package com.app.shahbaztrades.components.strategy.impl;

import com.app.shahbaztrades.components.strategy.AbstractContinuousTradingStrategy;
import com.app.shahbaztrades.service.MarginService;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.adx.MinusDIIndicator;
import org.ta4j.core.indicators.adx.PlusDIIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.rules.OverIndicatorRule;

@Component
public class Adx15Strategy extends AbstractContinuousTradingStrategy {

    public Adx15Strategy(MarginService marginService) {
        super(marginService);
    }

    @Override
    public String getName() {
        return "ADX15MIN";
    }

    @Override
    public String watchlistKey() {
        return "MACD15MINLOCAL";
    }

    @Override
    protected boolean matches(BarSeries series) {
        int safeClosedIndex = lastClosedIndex(series);
        int availableBars = safeClosedIndex - series.getBeginIndex() + 1;
        if (availableBars < 28) return false;
        return applyEntryRule(series, safeClosedIndex);
    }

    private boolean applyEntryRule(BarSeries series, int safeClosedIndex) {
        Bar current = series.getBar(safeClosedIndex);
        if (current.getOpenPrice().isGreaterThanOrEqual(current.getClosePrice())) {
            return false;
        }

        if (!isBullishClose(series, safeClosedIndex)) {
            return false;
        }

        var adx = new ADXIndicator(series, 14);
        var thresholdReached = new OverIndicatorRule(adx, 20.0);

        if (!thresholdReached.isSatisfied(safeClosedIndex)) {
            return false;
        }

        var adxMinus1 = new PreviousValueIndicator(adx, 1);
        var isAdxTurningUp = new OverIndicatorRule(adx, adxMinus1);
        if (!isAdxTurningUp.isSatisfied(safeClosedIndex)) {
            return false;
        }

        var plusDI = new PlusDIIndicator(series, 14);
        var minusDI = new MinusDIIndicator(series, 14);

        return new OverIndicatorRule(plusDI, minusDI).isSatisfied(safeClosedIndex);
    }

    private boolean isBullishClose(BarSeries series, int safeClosedIndex) {
        var bar = series.getBar(safeClosedIndex);
        var high = bar.getHighPrice();
        var low = bar.getLowPrice();
        var close = bar.getClosePrice();
        var range = high.minus(low);
        if (range.isLessThanOrEqual(series.numFactory().zero())) {
            return false;
        }

        var threshold = series.numFactory().numOf(0.7);
        return close.minus(low).dividedBy(range).isGreaterThan(threshold);
    }

}
