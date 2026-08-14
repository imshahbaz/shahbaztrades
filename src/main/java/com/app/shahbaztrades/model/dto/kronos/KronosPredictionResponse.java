package com.app.shahbaztrades.model.dto.kronos;

import com.app.shahbaztrades.model.dto.nse.NSEHistoricalData;
import com.app.shahbaztrades.model.entity.KronosPredictions;
import lombok.*;
import lombok.experimental.FieldDefaults;

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
    List<NSEHistoricalData> historicalData;
    List<NSEHistoricalData> predictions;

    public static KronosPredictionResponse fromKronosPrediction(KronosPredictions predictions, List<NSEHistoricalData> historicalData) {
        var builder = KronosPredictionResponse.builder().symbol(predictions.getSymbol())
                .runDate(predictions.getRunDate()).contextEndDate(predictions.getContextEndDate())
                .historicalData(historicalData);

        var predictedCandles = predictions.getPredictedCandles().stream()
                .sorted(Comparator.comparingInt(KronosPredictions.PredictedCandle::getHorizonDay))
                .map(candle -> NSEHistoricalData.builder().symbol(predictions.getSymbol()).open(candle.getOpen().doubleValue())
                        .high(candle.getHigh().doubleValue()).low(candle.getLow().doubleValue()).close(candle.getClose().doubleValue())
                        .timestamp(candle.getDate()).build())
                .toList();

        return builder.predictions(predictedCandles).build();
    }
}
