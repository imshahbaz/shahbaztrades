package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.chartink.ChartInkBacktestDto;
import com.app.shahbaztrades.model.dto.chartink.ChartInkBacktestMarginDto;
import com.app.shahbaztrades.model.dto.chartink.ChartInkResponseDto;
import com.app.shahbaztrades.model.dto.chartink.StockMarginDto;

import java.util.List;

public interface ChartInkService {

    void refreshTokens();

    ChartInkResponseDto fetchData(String strategyName);

    List<StockMarginDto> fetchWithMargin(String strategyName);

    List<ChartInkBacktestDto> fetchBacktestData(String strategyName);

    List<ChartInkBacktestMarginDto> fetchBacktestDataWithMargin(String strategyName);

    List<ChartInkBacktestMarginDto> fetchTodayBacktestDataWithMargin(String strategyName);
}
