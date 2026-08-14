package com.app.shahbaztrades.model.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldNameConstants
@Document(collection = "kronos_predictions")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KronosPredictions {

    @MongoId
    String id;

    String symbol;

    String runDate;

    String contextEndDate;

    BigDecimal anchorClose;

    Integer paths;

    List<PredictedCandle> predictedCandles = new ArrayList<>();

    @Builder.Default
    Instant createdAt = Instant.now();

    @Builder.Default
    Instant updatedAt = Instant.now();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class PredictedCandle {
        Integer horizonDay;

        String date;

        BigDecimal open;

        BigDecimal high;

        BigDecimal low;

        BigDecimal close;

        Long volume;

        BigDecimal returnPct;

        BigDecimal closeP10;

        BigDecimal closeP25;

        BigDecimal closeP75;

        BigDecimal closeP90;

    }

}
