package com.app.shahbaztrades.components.strategy.impl;

import com.app.shahbaztrades.components.strategy.AbstractContinuousTradingStrategy;
import com.app.shahbaztrades.service.MarginService;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;

@Component
public class InsideBar15Strategy extends AbstractContinuousTradingStrategy {

    protected InsideBar15Strategy(MarginService marginService) {
        super(marginService);
    }

    @Override
    public String getName() {
        return "INSIDE BAR";
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
        var mother = series.getBar(safeClosedIndex-1);
        if (mother.getClosePrice().isLessThanOrEqual(mother.getOpenPrice())){
            return false;
        }

        var child  = series.getBar(safeClosedIndex);
        return !mother.getHighPrice().isLessThanOrEqual(child.getHighPrice()) && !mother.getLowPrice().isGreaterThanOrEqual(child.getLowPrice());
    }

}
