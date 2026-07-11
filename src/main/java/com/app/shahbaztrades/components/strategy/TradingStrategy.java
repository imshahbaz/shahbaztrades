package com.app.shahbaztrades.components.strategy;

import com.app.shahbaztrades.model.entity.Margin;
import org.ta4j.core.BarSeries;

import java.util.List;
import java.util.Map;

public interface TradingStrategy {
    String getName();

    List<Margin> getFilteredMargins(List<BarSeries> barSeriesList, Map<String, String> tokenSymbolMap);

}