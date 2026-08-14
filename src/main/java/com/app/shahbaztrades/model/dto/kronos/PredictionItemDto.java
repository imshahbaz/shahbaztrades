package com.app.shahbaztrades.model.dto.kronos;

import com.app.shahbaztrades.model.entity.KronosPredictions;
import com.app.shahbaztrades.util.DateUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PredictionItemDto {

    String symbol;

    LocalDate runDate;

    LocalDate contextEndDate;

    Integer horizonDay;

    LocalDate date;

    BigDecimal anchorClose;

    BigDecimal open;

    BigDecimal high;

    BigDecimal low;

    BigDecimal close;

    Long volume;

    BigDecimal returnPct;

    Integer paths;

    @JsonProperty("close_p10")
    BigDecimal closeP10;

    @JsonProperty("close_p25")
    BigDecimal closeP25;

    @JsonProperty("close_p75")
    BigDecimal closeP75;

    @JsonProperty("close_p90")
    BigDecimal closeP90;

    public KronosPredictions mapKronosPredictions() {
        return KronosPredictions.builder().symbol(symbol).runDate(formatDate(runDate))
                .contextEndDate(formatDate(contextEndDate)).anchorClose(anchorClose)
                .paths(paths).build();
    }

    public KronosPredictions.PredictedCandle mapPredictedCandle() {
        return KronosPredictions.PredictedCandle.builder().horizonDay(horizonDay)
                .open(open).high(high).low(low).close(close).volume(volume)
                .returnPct(returnPct).closeP10(closeP10).closeP25(closeP25)
                .closeP75(closeP75).closeP90(closeP90).date(formatDate(date)).build();
    }

    private String formatDate(LocalDate date) {
        return date == null ? null : DateUtil.NSE_INPUT_LAYOUT.format(date);
    }

}