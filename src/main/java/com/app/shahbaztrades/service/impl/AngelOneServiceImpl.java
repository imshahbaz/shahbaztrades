package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.angelone.AngelOneClient;
import com.app.shahbaztrades.components.angelone.AngelOneRateLimiter;
import com.app.shahbaztrades.components.angelone.SmartApiFeignClient;
import com.app.shahbaztrades.components.helper.MarketDataContainer;
import com.app.shahbaztrades.components.observer.MarketTickPipeline;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.angelone.HistoricalDataRequest;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpDto;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;
import com.app.shahbaztrades.model.dto.angelone.websocket.*;
import com.app.shahbaztrades.model.entity.redis.AngelOneHistoricalDataRedis;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.repo.redis.AngelOneHistoricalDataRedisRepo;
import com.app.shahbaztrades.repo.redis.AngelOneLoginDataRedisRepo;
import com.app.shahbaztrades.repo.redis.MarketTickerRedisRepo;
import com.app.shahbaztrades.service.AngelOneService;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.util.DateUtil;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.annotation.PreDestroy;
import jakarta.websocket.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.container.jdk.client.JdkClientContainer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.app.shahbaztrades.util.Constants.AO_DATE_FORMATTER;
import static com.app.shahbaztrades.util.Constants.BEARER_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class AngelOneServiceImpl implements AngelOneService {

    private static final long RECONNECT_MAX_DELAY_SECONDS = 30;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 20;
    private final ConcurrentHashMap<String, Double> ltpCache = new ConcurrentHashMap<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean intentionalDisconnect = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final JsonMapper jsonMapper;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ReentrantLock wsLock = new ReentrantLock();
    private final AngelOneClient angelOneClient;
    private final MongoConfigService mongoConfigService;
    private final SmartApiFeignClient smartApiFeignClient;
    private final AngelOneRateLimiter angelOneRateLimiter;
    private final MarketDataContainer marketDataContainer;
    private final MarketTickPipeline marketTickPipeline;
    private final StrategyRegistry strategyRegistry;
    private final AngelOneHistoricalDataRedisRepo angelOneHistoricalDataRedisRepo;
    private final AngelOneLoginDataRedisRepo<AngelOneLoginResponse.LoginData> angelOneLoginDataRedisRepo;
    private final MarketTickerRedisRepo<SmartApiLtpResponse.MarketTicker> marketTickerRedisRepo;
    private final ClientManager.ReconnectHandler reconnectHandler = new ClientManager.ReconnectHandler() {
        @Override
        public boolean onDisconnect(CloseReason closeReason) {
            connected.set(false);
            if (intentionalDisconnect.get()) {
                log.info("Smart Stream closed intentionally; not reconnecting");
                return false;
            }
            int attempt = reconnectAttempts.incrementAndGet();
            log.warn("Smart Stream disconnected ({}). Scheduling reconnect attempt {}", closeReason, attempt);
            return true;
        }

        @Override
        public boolean onConnectFailure(Exception exception) {
            if (intentionalDisconnect.get()) return false;
            int attempt = reconnectAttempts.incrementAndGet();
            log.warn("Smart Stream reconnect attempt {} failed: {}", attempt, exception.getMessage());
            return true;
        }

        @Override
        public long getDelay() {
            int attempt = Math.max(1, reconnectAttempts.get());
            // 1s, 2s, 4s, 8s, 16s, then capped at 30s.
            long delay = 1L << Math.min(attempt - 1, 5);
            return Math.min(delay, RECONNECT_MAX_DELAY_SECONDS);
        }
    };
    private volatile Session session;
    private volatile ScheduledFuture<?> heartbeatTask;

    @Override
    public boolean isWebSocketConnected() {
        return connected.get();
    }

    @Override
    public int getReconnectAttempts() {
        return reconnectAttempts.get();
    }

    @Override
    public void startWebSocket() {
        if (connected.get() || mongoConfigService.getAngelOneJwtToken() == null) return;

        intentionalDisconnect.set(false);
        reconnectAttempts.set(0);

        ClientManager client = ClientManager.createClient(JdkClientContainer.class.getName());
        client.getProperties().put(ClientProperties.RECONNECT_HANDLER, reconnectHandler);

        ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                .configurator(new AngelOneHeaderConfigurator())
                .build();

        try {
            client.connectToServer(new AngelOneEndpoint(), config, URI.create(WS_URL));
        } catch (Exception e) {
            log.error("WebSocket Connection Error", e);
        }
    }

    private void resubscribeActiveTokens() {
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

    private void handleBinaryTick(ByteBuffer buffer) {
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        if (buffer.remaining() < 51 || buffer.get() != 1) {
            return;
        }

        byte[] tokenBytes = new byte[25];
        buffer.position(2);
        buffer.get(tokenBytes);
        String token = new String(tokenBytes, StandardCharsets.UTF_8).trim();
        double ltp = buffer.getInt(43) / 100.0;

        if (ltp <= 0) {
            return;
        }

        ltpCache.put(token, ltp);
        marketTickPipeline.publish(token, ltp);
        if (marketDataContainer.checkActiveWorker(token)) {
            marketDataContainer.getTickBuffer(token).add(
                    new LiveTick(ltp, ZonedDateTime.now(DateUtil.IST_ZONE))
            );
        }
    }

    @Override
    public void subscribe(String token, int exchangeType) {
        ltpCache.putIfAbsent(token, -1.0);

        var request = new SmartStreamRequest(
                "shahbaz_trades",
                1,
                new SmartStreamParams(1, List.of(new TokenGroup(exchangeType, List.of(token))))
        );
        send(request);
    }

    @Override
    public void unsubscribe(String token, int exchangeType) {
        var request = new SmartStreamRequest(
                "shahbaz_trades",
                2,
                new SmartStreamParams(1, List.of(new TokenGroup(exchangeType, List.of(token))))
        );
        send(request);
        ltpCache.remove(token);
    }

    private void send(Object obj) {
        Session local = session;
        if (local == null || !local.isOpen()) {
            throw new BadRequestException("Websocket is closed");
        }
        wsLock.lock();
        try {
            String json = jsonMapper.writeValueAsString(obj);
            log.info("Sending Subscription Request: {}", json);
            local.getBasicRemote().sendText(json);
        } catch (IOException e) {
            log.error("WebSocket Write Error", e);
        } finally {
            wsLock.unlock();
        }
    }

    private void startHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        heartbeatTask = scheduler.scheduleAtFixedRate(
                this::sendHeartbeat, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void sendHeartbeat() {
        Session local = session;
        if (connected.get() && local != null && local.isOpen()) {
            wsLock.lock();
            try {
                local.getBasicRemote().sendText("ping");
            } catch (IOException e) {
                log.error("Heartbeat failed", e);
            } finally {
                wsLock.unlock();
            }
        }
    }

    @Override
    public double getLTP(String token) {
        if (!connected.get()) return -2;
        return ltpCache.getOrDefault(token, -1.0);
    }

    @Override
    @EventListener(ApplicationReadyEvent.class)
    public void refreshBrokerSession() {
        AngelOneLoginResponse.LoginData loginData = angelOneLoginDataRedisRepo.get("oneklik");
        if (loginData != null) {
            var response = smartApiFeignClient.getUserProfile(BEARER_PREFIX + loginData.getJwtToken(), mongoConfigService.getConfig().getAngelOneConfig().getApiKey());
            if (response != null && response.status() != null && response.status()) {
                mongoConfigService.setAngelOneJwtToken(loginData.getJwtToken());
                mongoConfigService.setAngelOneFeedToken(loginData.getFeedToken());
                return;
            }
        }

        loginData = angelOneClient.getWebsocketLogin(mongoConfigService.getConfig().getAngelOneConfig());
        if (loginData != null) {
            mongoConfigService.setAngelOneJwtToken(loginData.getJwtToken());
            mongoConfigService.setAngelOneFeedToken(loginData.getFeedToken());
            angelOneLoginDataRedisRepo.set("oneklik", loginData, Duration.ofSeconds(DateUtil.zerodhaTokenExpiry()));
        }
    }

    @Override
    public void disconnect() {
        intentionalDisconnect.set(true);
        connected.set(false);

        wsLock.lock();
        try {
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
                heartbeatTask = null;
            }

            Session local = session;
            if (local != null && local.isOpen()) {
                try {
                    local.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "client disconnect"));
                    log.info("AngelOne WebSocket connection closed gracefully");
                } catch (IOException e) {
                    log.error("Error while closing WebSocket session", e);
                } finally {
                    session = null;
                }
            }
        } finally {
            wsLock.unlock();
        }

        ltpCache.clear();
    }

    @PreDestroy
    public void tearDown() {
        disconnect();
        scheduler.shutdownNow();
    }

    @Override
    public SmartApiLtpResponse.MarketTicker getMarketTicker(String token) {
        SmartApiLtpResponse.MarketTicker data = marketTickerRedisRepo.get(token);
        if (data != null) {
            return data;
        }

        var jwt = mongoConfigService.getAngelOneJwtToken();
        var response = smartApiFeignClient.getMultipleLtp(BEARER_PREFIX + jwt, mongoConfigService.getConfig().getAngelOneConfig().getApiKey(),
                SmartApiLtpDto.builder()
                        .mode("OHLC")
                        .exchangeTokens(Map.of(ExchangeType.NSE.name(), List.of(token)))
                        .build());

        if (response != null && response.data() != null && !CollectionUtils.isEmpty(response.data().getFetched())) {
            marketTickerRedisRepo.set(token, response.data().getFetched().getFirst(), DateUtil.getDurationUntilMarketOpen(Duration.ofMinutes(1)));
            return response.data().getFetched().getFirst();
        }

        throw new NotFoundException("Ltp not found");
    }

    @Override
    public Map<LocalDate, SmartApiLtpResponse.CandleDetail> getHistoricalData(String token, String symbol) {
        var optionalData = angelOneHistoricalDataRedisRepo.findById(symbol);
        if (optionalData.isPresent()) {
            var historicalData = optionalData.get();
            if (!CollectionUtils.isEmpty(historicalData.getDailyHistoricalData())) {
                return historicalData.getDailyHistoricalData().stream().collect(Collectors.toMap(
                        candle -> candle.timestamp().toLocalDate(),
                        candle -> candle
                ));
            }
        }

        var jwt = mongoConfigService.getAngelOneJwtToken();
        var today = DateUtil.getTodayDate();
        var thirtyDaysAgo = today.atTime(0, 0).minusDays(30);


        String fromDateStr = thirtyDaysAgo.format(AO_DATE_FORMATTER);
        String toDateStr = today.atTime(23, 59).format(AO_DATE_FORMATTER);

        var request = HistoricalDataRequest.builder()
                .exchange(ExchangeType.NSE.name())
                .symbolToken(token)
                .interval(ONE_DAY_INTERVAL)
                .fromDate(fromDateStr)
                .toDate(toDateStr)
                .build();

        angelOneRateLimiter.acquireHistoricalData();
        var response = smartApiFeignClient.getHistoricalData(BEARER_PREFIX + jwt, mongoConfigService.getConfig().getAngelOneConfig().getApiKey(), request);

        if (response != null) {
            var candles = response.getHistoricalCandles();

            AngelOneHistoricalDataRedis data;
            if (optionalData.isPresent()) {
                data = optionalData.get();
                data.setDailyHistoricalData(candles);
            } else {
                data = AngelOneHistoricalDataRedis.builder().id(symbol).dailyHistoricalData(candles)
                        .ttl(DateUtil.getDurationUntilMarketOpen(Duration.ofHours(1)).getSeconds()).build();
            }
            angelOneHistoricalDataRedisRepo.save(data);

            return candles.stream().collect(Collectors.toMap(
                    candle -> candle.timestamp().toLocalDate(),
                    candle -> candle
            ));
        }

        throw new NotFoundException("Historical data not found");
    }

    private class AngelOneHeaderConfigurator extends ClientEndpointConfig.Configurator {
        @Override
        public void beforeRequest(Map<String, List<String>> headers) {
            var angelOneConfig = mongoConfigService.getConfig().getAngelOneConfig();
            headers.put("Authorization", List.of(BEARER_PREFIX + mongoConfigService.getAngelOneJwtToken()));
            headers.put("x-api-key", List.of(angelOneConfig.getApiKey()));
            headers.put("x-client-code", List.of(angelOneConfig.getClientId()));
            headers.put("x-feed-token", List.of(mongoConfigService.getAngelOneFeedToken()));
        }
    }

    private class AngelOneEndpoint extends Endpoint {
        @Override
        public void onOpen(Session sess, EndpointConfig config) {
            session = sess;
            connected.set(true);

            sess.addMessageHandler(ByteBuffer.class, AngelOneServiceImpl.this::handleBinaryTick);
            sess.addMessageHandler(String.class, msg -> {
                if ("pong".equals(msg)) {
                    log.trace("Received keep-alive pong");
                }
            });

            startHeartbeat();
            if (reconnectAttempts.get() > 0) resubscribeActiveTokens();
            reconnectAttempts.set(0);
            log.info("Smart Stream Connected and Heartbeat started");
        }

        @Override
        public void onClose(Session sess, CloseReason closeReason) {
            connected.set(false);
            log.warn("Smart Stream Connection Closed: {}", closeReason);
        }

        @Override
        public void onError(Session sess, Throwable thr) {
            connected.set(false);
            log.error("Transport Error", thr);
        }
    }

}
