package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.angelone.SmartStreamConnection;
import com.app.shahbaztrades.components.angelone.SmartStreamTickDecoder;
import com.app.shahbaztrades.components.helper.MarketDataContainer;
import com.app.shahbaztrades.components.observer.MarketTickPipeline;
import com.app.shahbaztrades.model.dto.angelone.websocket.LiveTick;
import com.app.shahbaztrades.model.dto.angelone.websocket.Ltp;
import com.app.shahbaztrades.model.enums.ExchangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Live-price state layered over the websocket transport. */
@ExtendWith(MockitoExtension.class)
class AngelOneFeedServiceTest {

    @Mock
    private SmartStreamConnection connection;
    @Mock
    private MarketDataContainer marketDataContainer;
    @Mock
    private MarketTickPipeline marketTickPipeline;
    @Mock
    private StrategyRegistry strategyRegistry;

    private AngelOneFeedService service;

    @BeforeEach
    void setUp() {
        service = new AngelOneFeedService(connection, marketDataContainer, marketTickPipeline, strategyRegistry);
    }

    // --- ltp lookup -------------------------------------------------------

    @Test
    void getLtp_reportsFeedDownWhileTheSocketIsClosed() {
        when(connection.isConnected()).thenReturn(false);

        // Distinct from NOT_SUBSCRIBED: subscribing here cannot help, so callers must not wait.
        assertInstanceOf(Ltp.FeedDown.class, service.getLtp("11536"));
    }

    @Test
    void getLtp_reportsNotSubscribedForATokenThatHasNotTicked() {
        when(connection.isConnected()).thenReturn(true);

        assertInstanceOf(Ltp.NotSubscribed.class, service.getLtp("11536"));
    }

    @Test
    void getLtp_returnsTheLastTickedPrice() {
        when(connection.isConnected()).thenReturn(true);
        service.onTick(new SmartStreamTickDecoder.Tick("11536", 3200.5));

        assertEquals(new Ltp.Price(3200.5), service.getLtp("11536"));
    }

    @Test
    void getLtp_fallsBackToFeedDownWhenTheSocketDropsAfterATick() {
        when(connection.isConnected()).thenReturn(true, false);
        service.onTick(new SmartStreamTickDecoder.Tick("11536", 3200.5));
        service.getLtp("11536");

        // A stale cached price must never be served as live once the feed is gone.
        assertInstanceOf(Ltp.FeedDown.class, service.getLtp("11536"));
    }

    // --- tick fan-out -----------------------------------------------------

    @Test
    void onTick_publishesToThePipelineAndSkipsTheBufferWithoutAnActiveWorker() {
        when(marketDataContainer.checkActiveWorker("11536")).thenReturn(false);

        service.onTick(new SmartStreamTickDecoder.Tick("11536", 3200.5));

        verify(marketTickPipeline).publish("11536", 3200.5);
        verify(marketDataContainer, never()).getTickBuffer(anyString());
    }

    @Test
    void onTick_buffersTheTickForAnActiveBarSeriesWorker() {
        var buffer = new LinkedBlockingQueue<LiveTick>();
        when(marketDataContainer.checkActiveWorker("11536")).thenReturn(true);
        when(marketDataContainer.getTickBuffer("11536")).thenReturn(buffer);

        service.onTick(new SmartStreamTickDecoder.Tick("11536", 3200.5));

        assertEquals(1, buffer.size());
        assertEquals(3200.5, buffer.peek().price());
    }

    // --- subscriptions ----------------------------------------------------

    @Test
    void unsubscribe_dropsTheCachedPriceSoItCannotBeServedLater() {
        when(connection.isConnected()).thenReturn(true);
        service.onTick(new SmartStreamTickDecoder.Tick("11536", 3200.5));

        service.unsubscribe("11536", ExchangeType.NSE.getValue());

        assertInstanceOf(Ltp.NotSubscribed.class, service.getLtp("11536"));
    }

    @Test
    void onReconnected_replaysEverySubscriptionTheRegistryStillWants() {
        when(strategyRegistry.getAllActiveTokens()).thenReturn(List.of("11536", "1594"));

        service.onReconnected();

        verify(connection, org.mockito.Mockito.times(2)).send(any());
    }

    @Test
    void onReconnected_isANoOpWhenNothingIsBeingTracked() {
        when(strategyRegistry.getAllActiveTokens()).thenReturn(List.of());

        service.onReconnected();

        verify(connection, never()).send(any());
    }

    // --- lifecycle --------------------------------------------------------

    @Test
    void disconnect_clearsCachedPricesAlongsideTheSocket() {
        when(connection.isConnected()).thenReturn(true);
        service.onTick(new SmartStreamTickDecoder.Tick("11536", 3200.5));

        service.disconnect();

        verify(connection).disconnect();
        assertInstanceOf(Ltp.NotSubscribed.class, service.getLtp("11536"));
    }

    @Test
    void adminStateIsReadThroughFromTheConnection() {
        when(connection.isConnected()).thenReturn(false);
        when(connection.getReconnectAttempts()).thenReturn(3);

        assertFalse(service.isConnected());
        assertEquals(3, service.getReconnectAttempts());
    }
}
