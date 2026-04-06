package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.chartink.ChartInkBacktestDto;
import com.app.shahbaztrades.model.dto.chartink.ChartInkResponseDto;
import com.app.shahbaztrades.model.dto.chartink.StockMarginDto;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;

import java.util.List;

public interface ChartInkService {

    void refreshTokens();

    ChartInkResponseDto fetchData(StrategyDto strategy);

    List<StockMarginDto> fetchWithMargin(String strategyName);

    List<ChartInkBacktestDto> fetchBacktestData(String strategyName);
}
