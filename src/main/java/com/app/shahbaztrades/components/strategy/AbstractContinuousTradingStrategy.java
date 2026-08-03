package com.app.shahbaztrades.components.strategy;

import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.util.DateUtil;
import org.ta4j.core.BarSeries;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractContinuousTradingStrategy implements ContinuousTradingStrategy {

    protected final MarginService marginService;

    protected AbstractContinuousTradingStrategy(MarginService marginService) {
        this.marginService = marginService;
    }

    @Override
    public List<Margin> getFilteredMargins(List<BarSeries> barSeriesList, Map<String, String> tokenSymbolMap) {
        return barSeriesList.parallelStream()
                .map(barSeries -> {
                    String symbol = tokenSymbolMap.get(barSeries.getName());
                    if (symbol == null) return null;

                    var margin = marginService.getMarginCache().get(symbol);
                    if (margin == null) return null;

                    return matches(barSeries) ? margin : null;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Margin::getRequiredMargin).reversed())
                .toList();
    }

    protected int lastClosedIndex(BarSeries series) {
        if (series.isEmpty())
            return -1;

        var now = DateUtil.getCurrentDateTime().atZone(DateUtil.IST_ZONE).toInstant();
        var isFirstScan = LocalTime.now(DateUtil.IST_ZONE).withSecond(0).withNano(0).equals(MARKET_START_TIME);
        for (int i = series.getEndIndex(); i >= series.getBeginIndex(); i--) {
            var bar = series.getBar(i);
            if (bar.getEndTime().isBefore(now)) {
                if (isFirstScan || now.isBefore(bar.getEndTime().plus(bar.getTimePeriod()))) {
                    return i;
                }
                return -1;
            }
        }

        return -1;
    }

    protected abstract boolean matches(BarSeries series);
}
