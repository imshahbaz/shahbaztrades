package com.app.shahbaztrades.model.dto.angelone;

import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.market.Candle;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.util.CollectionUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public record SmartApiLtpResponse<T>(
        Boolean status,
        String message,
        String errorcode,
        T data
) {
    private Candle mapCandle(List<Object> candle) {
        if (CollectionUtils.isEmpty(candle)) {
            return null;
        }

        return Candle.builder()
                .timestamp(ZonedDateTime.parse((String) candle.getFirst()))
                .open((Double) candle.get(1))
                .high((Double) candle.get(2))
                .low((Double) candle.get(3))
                .close((Double) candle.get(4))
                .build();
    }

    public boolean isSuccess() {
        return status != null && status && data != null;
    }

    public List<Candle> getHistoricalCandles() {
        if (!isSuccess()) {
            throw new NotFoundException("Historical data not found");
        }

        var list = new ArrayList<Candle>();
        for (var candle : (List<List<Object>>) data) {
            var detail = mapCandle(candle);
            if (detail != null) {
                list.add(detail);
            }
        }
        return list;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class MarketData {
        List<MarketTicker> fetched;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class MarketTicker implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        String exchange;
        String tradingSymbol;
        String symbolToken;
        Double ltp;
        Double open;
        Double high;
        Double low;
        Double close;
    }

}