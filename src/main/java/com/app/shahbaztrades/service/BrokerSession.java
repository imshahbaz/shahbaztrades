package com.app.shahbaztrades.service;

/**
 * Broker credentials and their renewal. Split out of the old {@code AngelOneService} so the
 * websocket transport and the REST data client can read tokens without depending on the feed.
 */
public interface BrokerSession {

    /** Reuses a cached login if it still validates, otherwise performs a fresh broker login. */
    void refreshBrokerSession();

    String jwtToken();

    String feedToken();

    String apiKey();

    String clientId();
}
