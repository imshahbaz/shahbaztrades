package com.app.shahbaztrades.components.strategy;

import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.util.DateUtil;
import org.ta4j.core.BarSeries;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractTradingStrategy implements TradingStrategy {

    protected final MarginService marginService;

    protected AbstractTradingStrategy(MarginService marginService) {
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
                .sorted(Comparator.comparingDouble(Margin::getRequiredMargin).reversed())
                .toList();
    }

    protected int lastClosedIndex(BarSeries series) {
        var now = DateUtil.getCurrentDateTime().atZone(DateUtil.IST_ZONE);
        for (int i = series.getEndIndex(); i >= series.getBeginIndex(); i--) {
            if (series.getBar(i).getEndTime().atZone(DateUtil.IST_ZONE).isBefore(now)) {
                return i;
            }
        }
        return -1;
    }

    protected abstract boolean matches(BarSeries series);
}
