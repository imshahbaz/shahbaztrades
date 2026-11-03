package com.app.shahbaztrades.model.dto.market;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.ZonedDateTime;

/**
 * A single OHLC bar stamped with the instant its interval began. Used for both daily and
 * intraday market data regardless of which broker or feed produced it.
 */
@Builder
public record Candle(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        ZonedDateTime timestamp,
        double open,
        double high,
        double low,
        double close
) {
}
