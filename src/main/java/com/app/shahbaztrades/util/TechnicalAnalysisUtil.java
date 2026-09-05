package com.app.shahbaztrades.util;

import com.app.shahbaztrades.model.dto.analysis.TechnicalMetrics;
import com.app.shahbaztrades.model.dto.market.Candle;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.num.DecimalNum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TechnicalAnalysisUtil {

    public static TechnicalMetrics getAtr(List<Candle> data) {
        BarSeries series = new BaseBarSeriesBuilder().build();

        for (var candle : data) {
            ZonedDateTime zonedDateTime = candle.timestamp();

            Bar bar = new BaseBar(
                    Duration.ofDays(1),
                    zonedDateTime.toInstant(),
                    zonedDateTime.plusDays(1).toInstant(),
                    DecimalNum.valueOf(candle.open()),
                    DecimalNum.valueOf(candle.high()),
                    DecimalNum.valueOf(candle.low()),
                    DecimalNum.valueOf(candle.close()),
                    DecimalNum.valueOf(0.0),
                    DecimalNum.valueOf(0.0),
                    0L
            );

            series.addBar(bar);
        }

        ATRIndicator atrIndicator = new ATRIndicator(series, 14);

        int latestIndex = series.getEndIndex();
        double finalAtr = atrIndicator.getValue(latestIndex).doubleValue();
        double latestClose = data.getLast().close();

        return TechnicalMetrics.builder()
                .atrValue(BigDecimal.valueOf(finalAtr)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue())
                .expectedMovePercent(BigDecimal.valueOf((finalAtr / latestClose) * 100)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue())
                .build();
    }
}
