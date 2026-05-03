package com.app.shahbaztrades.model.dto.chartink;

import java.util.List;

public record ChartInkSignalEvent(String strategyName, List<ChartInkBacktestMarginDto> signals) {
}