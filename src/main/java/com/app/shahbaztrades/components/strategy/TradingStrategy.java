package com.app.shahbaztrades.components.strategy;

import com.app.shahbaztrades.model.entity.Margin;
import org.ta4j.core.BarSeries;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public interface TradingStrategy {

    LocalTime MARKET_START_TIME = LocalTime.of(9, 15);

    String getName();

    List<Margin> getFilteredMargins(List<BarSeries> barSeriesList, Map<String, String> tokenSymbolMap);

}