package com.app.shahbaztrades.components.strategy.impl;

import com.app.shahbaztrades.components.strategy.AbstractContinuousTradingStrategy;
import com.app.shahbaztrades.service.MarginService;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.UnderIndicatorRule;

@Component
public class Rsi15Strategy extends AbstractContinuousTradingStrategy {

    public Rsi15Strategy(MarginService marginService) {
        super(marginService);
    }

    @Override
    public String getName() {
        return "RSI15MIN";
    }

    @Override
    protected boolean matches(BarSeries series) {
        int safeClosedIndex = lastClosedIndex(series);
        int availableBars = safeClosedIndex - series.getBeginIndex() + 1;
        if (availableBars < 14) return false;

        return applyEntryRule(series, safeClosedIndex);
    }

    private boolean applyEntryRule(BarSeries series, int safeClosedIndex) {
        Bar current = series.getBar(safeClosedIndex);
        if (current.getOpenPrice().isGreaterThanOrEqual(current.getClosePrice())) {
            return false;
        }

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(close, 14);
        Rule isOversold = new UnderIndicatorRule(rsi, 35.0);
        return isOversold.isSatisfied(safeClosedIndex);
    }

}
