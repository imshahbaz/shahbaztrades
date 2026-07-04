package com.app.shahbaztrades.model.dto.angelone;

import com.app.shahbaztrades.exceptions.NotFoundException;
import lombok.Builder;
import org.springframework.util.CollectionUtils;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public record SmartApiLtpResponse<T>(
        Boolean status,
        String message,
        String errorcode,
        T data
) {
    public record MarketData(
            List<MarketTicker> fetched
    ) {
    }

    public record MarketTicker(
            String exchange,
            String tradingSymbol,
            String symbolToken,
            Double ltp,
            Double open,
            Double high,
            Double low,
            Double close
    ) {
    }

    @Builder
    public record CandleDetail(
            ZonedDateTime timestamp,
            double open,
            double high,
            double low,
            double close
    ) {
    }

    private CandleDetail mapCandleDetail(List<Object> candle) {
        if (CollectionUtils.isEmpty(candle)) {
            return null;
        }

        return CandleDetail.builder()
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

    public List<CandleDetail> getHistoricalCandles() {
        if (!isSuccess()) {
            throw new NotFoundException("Historical data not found");
        }

        var list = new ArrayList<CandleDetail>();
        for (var candle : (List<List<Object>>) data) {
            var detail = mapCandleDetail(candle);
            if (detail != null) {
                list.add(detail);
            }
        }
        return list;
    }

}