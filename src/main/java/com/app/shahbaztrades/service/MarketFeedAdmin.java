package com.app.shahbaztrades.service;

/** Feed lifecycle and health. Used by ops endpoints only; trading code wants {@link MarketFeed}. */
public interface MarketFeedAdmin {

    void start();

    void disconnect();

    boolean isConnected();

    int getReconnectAttempts();
}
