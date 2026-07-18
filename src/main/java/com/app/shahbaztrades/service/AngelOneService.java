package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;

import java.time.LocalDate;
import java.util.Map;

public interface AngelOneService {

    void startWebSocket();

    void subscribe(String token, int exchangeType);

    void unsubscribe(String token, int exchangeType);

    double getLTP(String token);

    void refreshBrokerSession();

    void disconnect();

    SmartApiLtpResponse.MarketTicker getMarketTicker(String token);

    Map<LocalDate, SmartApiLtpResponse.CandleDetail> getHistoricalData(String token, String symbol);
}
