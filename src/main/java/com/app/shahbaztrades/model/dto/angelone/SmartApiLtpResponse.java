package com.app.shahbaztrades.model.dto.angelone;

import java.util.List;

public record SmartApiLtpResponse(
        Boolean status,
        String message,
        String errorcode,
        MarketData data
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
}