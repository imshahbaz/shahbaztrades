package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** On-demand market data pulled over REST and cached, as opposed to the streaming {@link MarketFeed}. */
public interface MarketDataQuery {

    SmartApiLtpResponse.MarketTicker getMarketTicker(String token);

    Map<LocalDate, SmartApiLtpResponse.CandleDetail> getHistoricalData(String token, String symbol);

    /** Recent 15-minute candles used to seed a live bar series before the session starts. */
    List<SmartApiLtpResponse.CandleDetail> getFifteenMinuteCandles(String token, String symbol);
}
