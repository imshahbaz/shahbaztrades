package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.angelone.websocket.Ltp;

/** Live price feed, as seen by trading code. */
public interface MarketFeed {

    void subscribe(String token, int exchangeType);

    void unsubscribe(String token, int exchangeType);

    Ltp getLtp(String token);
}
