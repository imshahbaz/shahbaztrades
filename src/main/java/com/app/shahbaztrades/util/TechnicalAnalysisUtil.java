package com.app.shahbaztrades.util;

import com.app.shahbaztrades.model.dto.analysis.TechnicalMetrics;
import com.app.shahbaztrades.model.dto.nse.NSEHistoricalData;
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
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TechnicalAnalysisUtil {

    public static TechnicalMetrics getAtr(List<NSEHistoricalData> data) {
        BarSeries series = new BaseBarSeriesBuilder().build();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

        for (var candle : data) {
            LocalDate localDate = LocalDate.parse(candle.getTimestamp(), formatter);
            ZonedDateTime zonedDateTime = localDate.atStartOfDay(DateUtil.IST_ZONE);

            Bar bar = new BaseBar(
                    Duration.ofDays(1),
                    zonedDateTime.toInstant(),
                    zonedDateTime.plusDays(1).toInstant(),
                    DecimalNum.valueOf(candle.getOpen()),
                    DecimalNum.valueOf(candle.getHigh()),
                    DecimalNum.valueOf(candle.getLow()),
                    DecimalNum.valueOf(candle.getClose()),
                    DecimalNum.valueOf(0.0),
                    DecimalNum.valueOf(0.0),
                    0L
            );

            series.addBar(bar);
        }

        ATRIndicator atrIndicator = new ATRIndicator(series, 14);

        int latestIndex = series.getEndIndex();
        double finalAtr = atrIndicator.getValue(latestIndex).doubleValue();
        double latestClose = data.getLast().getClose();

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
