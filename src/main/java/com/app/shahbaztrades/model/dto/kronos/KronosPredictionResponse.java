package com.app.shahbaztrades.model.dto.kronos;

import com.app.shahbaztrades.model.dto.market.Candle;
import com.app.shahbaztrades.model.entity.KronosPredictions;
import com.app.shahbaztrades.util.DateUtil;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KronosPredictionResponse {
    String symbol;
    String runDate;
    String contextEndDate;
    List<Candle> historicalData;
    List<Candle> predictions;

    public static KronosPredictionResponse fromKronosPrediction(KronosPredictions predictions, List<Candle> historicalData) {
        var builder = KronosPredictionResponse.builder().symbol(predictions.getSymbol())
                .runDate(predictions.getRunDate()).contextEndDate(predictions.getContextEndDate())
                .historicalData(historicalData);

        var predictedCandles = predictions.getPredictedCandles().stream()
                .sorted(Comparator.comparingInt(KronosPredictions.PredictedCandle::getHorizonDay))
                .map(candle -> Candle.builder().open(candle.getOpen().doubleValue())
                        .high(candle.getHigh().doubleValue()).low(candle.getLow().doubleValue()).close(candle.getClose().doubleValue())
                        .timestamp(LocalDate.parse(candle.getDate(), DateUtil.NSE_INPUT_LAYOUT).atStartOfDay(DateUtil.IST_ZONE))
                        .build())
                .toList();

        return builder.predictions(predictedCandles).build();
    }
}
