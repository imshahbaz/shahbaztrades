package com.app.shahbaztrades.components.strategy.impl;

import com.app.shahbaztrades.components.strategy.TradingStrategy;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component("RSI15MIN")
@RequiredArgsConstructor
public class Rsi15Strategy implements TradingStrategy {

    private final MarginService marginService;

    @Override
    public String getName() {
        return "RSI15MIN";
    }

    @Override
    public List<Margin> getFilteredMargins(List<BarSeries> barSeriesList, Map<String, String> tokenSymbolMap) {
        return barSeriesList.parallelStream()
                .map(barSeries -> {
                    String symbol = tokenSymbolMap.get(barSeries.getName());
                    if (symbol == null) return null;

                    var margin = marginService.getMarginCache().get(symbol);
                    if (margin == null) return null;

                    if (checkSetup(barSeries)) return margin;

                    return null;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(Margin::getRequiredMargin).reversed())
                .toList();
    }

    private boolean checkSetup(BarSeries series) {
        if (series.getBarCount() < 14) return false;

        int safeClosedIndex = -1;
        for (int i = series.getEndIndex(); i >= series.getBeginIndex(); i--) {
            var candleEndTime = series.getBar(i).getEndTime().atZone(DateUtil.IST_ZONE);
            if (candleEndTime.isBefore(DateUtil.getCurrentDateTime().atZone(DateUtil.IST_ZONE))) {
                safeClosedIndex = i;
                break;
            }
        }

        if (safeClosedIndex < 14) return false;


        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);

        double confirmedRsi = rsi.getValue(safeClosedIndex).doubleValue();
        boolean isOversold = confirmedRsi < 35.0;

        var closedBar = series.getBar(safeClosedIndex);
        boolean isGreenCandle = closedBar.getClosePrice().isGreaterThan(closedBar.getOpenPrice());

        return isOversold && isGreenCandle;
    }
}
