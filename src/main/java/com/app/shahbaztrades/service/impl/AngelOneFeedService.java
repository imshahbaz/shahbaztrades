package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.angelone.SmartStreamConnection;
import com.app.shahbaztrades.components.angelone.SmartStreamTickDecoder;
import com.app.shahbaztrades.components.helper.MarketDataContainer;
import com.app.shahbaztrades.components.observer.MarketTickPipeline;
import com.app.shahbaztrades.model.dto.angelone.websocket.LiveTick;
import com.app.shahbaztrades.model.dto.angelone.websocket.Ltp;
import com.app.shahbaztrades.model.dto.angelone.websocket.SmartStreamParams;
import com.app.shahbaztrades.model.dto.angelone.websocket.SmartStreamRequest;
import com.app.shahbaztrades.model.dto.angelone.websocket.TokenGroup;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.service.MarketFeed;
import com.app.shahbaztrades.service.MarketFeedAdmin;
import com.app.shahbaztrades.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live price state on top of {@link SmartStreamConnection}: keeps the last tick per token and
 * fans each tick out to the disruptor pipeline and the bar-series container.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AngelOneFeedService implements MarketFeed, MarketFeedAdmin, SmartStreamConnection.Listener {

    private static final String CLIENT_CODE = "shahbaz_trades";
    private static final int ACTION_SUBSCRIBE = 1;
    private static final int ACTION_UNSUBSCRIBE = 2;
    private static final int MODE_LTP = 1;

    /** Only ever holds positive prices; the decoder discards the rest. */
    private final ConcurrentHashMap<String, Double> ltpCache = new ConcurrentHashMap<>();
    private final SmartStreamConnection connection;
    private final MarketDataContainer marketDataContainer;
    private final MarketTickPipeline marketTickPipeline;
    private final StrategyRegistry strategyRegistry;

    @Override
    public void start() {
        connection.connect(this);
    }

    @Override
    public void disconnect() {
        connection.disconnect();
        ltpCache.clear();
    }

    @Override
    public boolean isConnected() {
        return connection.isConnected();
    }

    @Override
    public int getReconnectAttempts() {
        return connection.getReconnectAttempts();
    }

    @Override
    public Ltp getLtp(String token) {
        if (!connection.isConnected()) {
            return Ltp.FEED_DOWN;
        }
        Double cached = ltpCache.get(token);
        return cached == null ? Ltp.NOT_SUBSCRIBED : Ltp.of(cached);
    }

    @Override
    public void subscribe(String token, int exchangeType) {
        connection.send(streamRequest(ACTION_SUBSCRIBE, token, exchangeType));
    }

    @Override
    public void unsubscribe(String token, int exchangeType) {
        connection.send(streamRequest(ACTION_UNSUBSCRIBE, token, exchangeType));
        ltpCache.remove(token);
    }

    @Override
    public void onTick(SmartStreamTickDecoder.Tick tick) {
        ltpCache.put(tick.token(), tick.ltp());
        marketTickPipeline.publish(tick.token(), tick.ltp());
        if (marketDataContainer.checkActiveWorker(tick.token())) {
            marketDataContainer.getTickBuffer(tick.token())
                    .add(new LiveTick(tick.ltp(), ZonedDateTime.now(DateUtil.IST_ZONE)));
        }
    }

    @Override
    public void onReconnected() {
        var tokens = strategyRegistry.getAllActiveTokens();
        if (CollectionUtils.isEmpty(tokens)) return;
        for (String token : tokens) {
            try {
                subscribe(token, ExchangeType.NSE.getValue());
            } catch (Exception e) {
                log.error("Resubscribe failed for token: {}", token, e);
            }
        }
        log.info("Resubscribed {} active token(s) after connect", tokens.size());
    }

    private SmartStreamRequest streamRequest(int action, String token, int exchangeType) {
        return new SmartStreamRequest(
                CLIENT_CODE,
                action,
                new SmartStreamParams(MODE_LTP, List.of(new TokenGroup(exchangeType, List.of(token))))
        );
    }
}
