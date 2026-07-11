package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.angelone.AngelOneClient;
import com.app.shahbaztrades.components.angelone.SmartApiFeignClient;
import com.app.shahbaztrades.components.helper.MarketDataContainer;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.angelone.HistoricalDataRequest;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpDto;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;
import com.app.shahbaztrades.model.dto.angelone.websocket.*;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.service.AngelOneService;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.app.shahbaztrades.util.Constants.AO_DATE_FORMATTER;
import static com.app.shahbaztrades.util.Constants.BEARER_PREFIX;


@Slf4j
@Service
@RequiredArgsConstructor
public class AngelOneServiceImpl implements WebSocketHandler, AngelOneService {

    private static final String WS_URL = "wss://smartapisocket.angelone.in/smart-stream";
    private final ConcurrentHashMap<String, Double> ltpCache = new ConcurrentHashMap<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final StringRedisTemplate stringRedisTemplate;
    private final AngelOneClient angelOneClient;
    private final MongoConfigService mongoConfigService;
    private final SmartApiFeignClient smartApiFeignClient;
    private final MarketDataContainer marketDataContainer;
    private WebSocketSession session;
    private ScheduledFuture<?> heartbeatTask;

    @Override
    public void startWebSocket() {
        if (connected.get() || mongoConfigService.getAngelOneJwtToken() == null) return;

        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Authorization", BEARER_PREFIX + mongoConfigService.getAngelOneJwtToken());
        headers.add("x-api-key", mongoConfigService.getConfig().getAngelOneConfig().getApiKey());
        headers.add("x-client-code", mongoConfigService.getConfig().getAngelOneConfig().getClientId());
        headers.add("x-feed-token", mongoConfigService.getAngelOneFeedToken());

        try {
            session = client.execute(this, headers, java.net.URI.create(WS_URL)).get();
        } catch (Exception e) {
            log.error("WebSocket Connection Error", e);
        }
    }

    @Override
    public void handleMessage(@NonNull WebSocketSession session, @NonNull WebSocketMessage<?> message) {
        if (message instanceof BinaryMessage binaryMessage) {
            ByteBuffer buffer = binaryMessage.getPayload();
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            if (buffer.remaining() >= 51) {
                byte firstByte = buffer.get();

                if (firstByte == 1) {
                    byte[] tokenBytes = new byte[25];
                    buffer.position(2);
                    buffer.get(tokenBytes);
                    String token = new String(tokenBytes, StandardCharsets.UTF_8).trim();
                    int priceInt = buffer.getInt(43);
                    double ltp = priceInt / 100.0;

                    if (ltp > 0) {
                        ltpCache.put(token, ltp);
                        marketDataContainer.getTickBuffer(token).add(
                                new LiveTick(ltp, ZonedDateTime.now(DateUtil.IST_ZONE))
                        );
                    }
                }
            }
        } else if (message instanceof TextMessage textMessage && "pong".equals(textMessage.getPayload())) {
            log.trace("Received keep-alive pong");
        }
    }

    @Override
    public synchronized void subscribe(String token, int exchangeType) {
        ltpCache.putIfAbsent(token, -1.0);

        var request = new SmartStreamRequest(
                "shahbaz_trades",
                1,
                new SmartStreamParams(1, List.of(new TokenGroup(exchangeType, List.of(token))))
        );
        send(request);
    }

    @Override
    public synchronized void unsubscribe(String token, int exchangeType) {
        var request = new SmartStreamRequest(
                "shahbaz_trades",
                2,
                new SmartStreamParams(1, List.of(new TokenGroup(exchangeType, List.of(token))))
        );
        send(request);
        ltpCache.remove(token);
    }

    private void send(Object obj) {
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(obj);
                log.info("Sending Subscription Request: {}", json);
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                log.error("WebSocket Write Error", e);
            }
        } else throw new BadRequestException("Websocket is closed");
    }

    private void sendHeartbeat() {
        if (connected.get() && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage("ping"));
            } catch (IOException e) {
                log.error("Heartbeat failed", e);
            }
        }
    }

    @Override
    public double getLTP(String token) {
        if (!connected.get()) return -2;
        return ltpCache.getOrDefault(token, -1.0);
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        this.session = session;
        this.connected.set(true);
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        heartbeatTask = scheduler.scheduleAtFixedRate(this::sendHeartbeat, 20, 20, TimeUnit.SECONDS);
        log.info("Smart Stream Connected and Heartbeat started");
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        this.connected.set(false);
        log.error("Transport Error", exception);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus closeStatus) {
        this.connected.set(false);
        this.ltpCache.clear();
        log.warn("Smart Stream Connection Closed");
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    @Override
    @EventListener(ApplicationReadyEvent.class)
    public void refreshBrokerSession() {
        var key = "angel_one_login_data";
        var data = stringRedisTemplate.opsForValue().get(key);
        AngelOneLoginResponse.LoginData loginData;
        if (StringUtils.isNotEmpty(data)) {
            loginData = HelperUtil.GSON.fromJson(data, AngelOneLoginResponse.LoginData.class);
            var response = smartApiFeignClient.getUserProfile(BEARER_PREFIX + loginData.getJwtToken(), mongoConfigService.getConfig().getAngelOneConfig().getApiKey());
            if (response != null && response.status() != null && response.status()) {
                mongoConfigService.setAngelOneJwtToken(loginData.getJwtToken());
                mongoConfigService.setAngelOneFeedToken(loginData.getFeedToken());
                return;
            }
        }

        loginData = angelOneClient.getWebsocketLogin(mongoConfigService.getConfig().getAngelOneConfig());
        if (loginData != null) {
            stringRedisTemplate.opsForValue().set(key, HelperUtil.GSON.toJson(loginData), Duration.ofSeconds(DateUtil.zerodhaTokenExpiry()));
            mongoConfigService.setAngelOneJwtToken(loginData.getJwtToken());
            mongoConfigService.setAngelOneFeedToken(loginData.getFeedToken());
        }
    }

    @Override
    public void disconnect() {
        connected.set(false);

        synchronized (this) {
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
                heartbeatTask = null;
            }

            if (session != null && session.isOpen()) {
                try {
                    session.close(CloseStatus.NORMAL);
                    log.info("AngelOne WebSocket connection closed gracefully");
                } catch (IOException e) {
                    log.error("Error while closing WebSocket session", e);
                } finally {
                    session = null;
                }
            }
        }

        ltpCache.clear();
    }

    @PreDestroy
    public void tearDown() {
        disconnect();
        scheduler.shutdownNow();
    }

    @Override
    public ResponseEntity<ApiResponse<SmartApiLtpResponse.MarketTicker>> getMarketTicker(String token) {
        var key = "angel_one_ltp:" + token;
        var data = stringRedisTemplate.opsForValue().get(key);
        if (data != null) {
            return ResponseEntity.ok(ApiResponse.ok(HelperUtil.GSON.fromJson(data, SmartApiLtpResponse.MarketTicker.class), "Ltp Fetched Successfully"));
        }

        var jwt = mongoConfigService.getAngelOneJwtToken();
        var response = smartApiFeignClient.getMultipleLtp(BEARER_PREFIX + jwt, mongoConfigService.getConfig().getAngelOneConfig().getApiKey(),
                SmartApiLtpDto.builder()
                        .mode("OHLC")
                        .exchangeTokens(Map.of(ExchangeType.NSE.name(), List.of(token)))
                        .build());

        if (response != null && response.data() != null && !CollectionUtils.isEmpty(response.data().fetched())) {
            stringRedisTemplate.opsForValue().set(key, HelperUtil.GSON.toJson(response.data().fetched().getFirst()), DateUtil.getDurationUntilMarketOpen(Duration.ofMinutes(1)));
            return ResponseEntity.ok(ApiResponse.ok(response.data().fetched().getFirst(), "Ltp Fetched Successfully"));
        }

        throw new NotFoundException("Ltp not found");
    }

    @Override
    public Map<LocalDate, SmartApiLtpResponse.CandleDetail> getHistoricalData(String token, String symbol) {
        var key = "angel_one_historical_data:" + symbol;
        var data = stringRedisTemplate.opsForValue().get(key);
        if (data != null) {
            Map<String, SmartApiLtpResponse.CandleDetail> stringKeyMap = HelperUtil.GSON.fromJson(
                    data,
                    new TypeToken<Map<String, SmartApiLtpResponse.CandleDetail>>() {
                    }.getType()
            );

            Map<LocalDate, SmartApiLtpResponse.CandleDetail> cachedResult = new HashMap<>();
            stringKeyMap.forEach((dateStr, candle) -> cachedResult.put(LocalDate.parse(dateStr), candle));
            return cachedResult;
        }

        var jwt = mongoConfigService.getAngelOneJwtToken();
        var today = DateUtil.getTodayDate();
        var thirtyDaysAgo = today.atTime(0, 0).minusDays(30);


        String fromDateStr = thirtyDaysAgo.format(AO_DATE_FORMATTER);
        String toDateStr = today.atTime(23, 59).format(AO_DATE_FORMATTER);

        var request = HistoricalDataRequest.builder()
                .exchange("NSE")
                .symbolToken(token)
                .interval("ONE_DAY")
                .fromDate(fromDateStr)
                .toDate(toDateStr)
                .build();

        var response = smartApiFeignClient.getHistoricalData(BEARER_PREFIX + jwt, mongoConfigService.getConfig().getAngelOneConfig().getApiKey(), request);

        if (response != null) {
            var candles = response.getHistoricalCandles();
            Map<LocalDate, SmartApiLtpResponse.CandleDetail> candleDetails = new HashMap<>();
            Map<String, SmartApiLtpResponse.CandleDetail> cachePersistenceMap = new HashMap<>();
            for (var candle : candles) {
                cachePersistenceMap.put(candle.timestamp().toLocalDate().toString(), candle);
                candleDetails.put(candle.timestamp().toLocalDate(), candle);
            }

            stringRedisTemplate.opsForValue().set(key, HelperUtil.GSON.toJson(cachePersistenceMap), DateUtil.getDurationUntilMarketOpen(Duration.ofHours(1)));
            return candleDetails;
        }

        throw new NotFoundException("Historical data not found");
    }

}