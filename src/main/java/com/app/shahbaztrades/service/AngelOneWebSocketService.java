package com.app.shahbaztrades.service;

public interface AngelOneWebSocketService {
    void setConfig(String jwt, String feedToken, String apiKey, String clientCode);

    void startWebSocket();

    void subscribe(String token, int exchangeType);

    void unsubscribe(String token, int exchangeType);

    double getLTP(String token);

    void refreshBrokerSession();

    void disconnect();
}
