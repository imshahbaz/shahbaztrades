package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.angelone.AngelOneClient;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.model.dto.angelone.websocket.AngelOneLoginResponse;
import com.app.shahbaztrades.model.dto.angelone.websocket.SmartStreamParams;
import com.app.shahbaztrades.model.dto.angelone.websocket.SmartStreamRequest;
import com.app.shahbaztrades.model.dto.angelone.websocket.TokenGroup;
import com.app.shahbaztrades.service.AngelOneWebSocketService;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


@Slf4j
@Service
@RequiredArgsConstructor
public class AngelOneWebSocketServiceImpl implements WebSocketHandler, AngelOneWebSocketService {

    private static final String WS_URL = "wss://smartapisocket.angelone.in/smart-stream";
    private final ConcurrentHashMap<String, Double> ltpCache = new ConcurrentHashMap<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final StringRedisTemplate stringRedisTemplate;
    private final AngelOneClient angelOneClient;
    private final MongoConfigService mongoConfigService;
    private WebSocketSession session;
    private String jwt, apiKey, clientCode, feedToken;

    @Override
    public void setConfig(String jwt, String feedToken, String apiKey, String clientCode) {
        this.jwt = jwt;
        this.feedToken = feedToken;
        this.apiKey = apiKey;
        this.clientCode = clientCode;
    }

    @Override
    public void startWebSocket() {
        if (connected.get() || jwt == null) return;

        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Authorization", "Bearer " + jwt);
        headers.add("x-api-key", apiKey);
        headers.add("x-client-code", clientCode);
        headers.add("x-feed-token", feedToken);

        try {
            session = client.execute(this, headers, java.net.URI.create(WS_URL)).get();
        } catch (Exception e) {
            log.error("WebSocket Connection Error: {}", e.getMessage());
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
                    }
                }
            }
        } else if (message instanceof TextMessage textMessage) {
            if ("pong".equals(textMessage.getPayload())) {
                log.trace("Received keep-alive pong");
            }
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
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 20, 20, TimeUnit.SECONDS);
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
    @PostConstruct
    public void refreshBrokerSession() {
        var key = "angel_one_login_data";
        var login = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isNotEmpty(login)) {
            var loginData = HelperUtil.GSON.fromJson(login, AngelOneLoginResponse.LoginData.class);
            this.setConfig(loginData.getJwtToken(), loginData.getFeedToken(),
                    mongoConfigService.getConfig().getAngelOneConfig().getApiKey(),
                    mongoConfigService.getConfig().getAngelOneConfig().getClientId());
            return;
        }

        var loginData = angelOneClient.getWebsocketLogin(mongoConfigService.getConfig().getAngelOneConfig());
        if (loginData != null) {
            stringRedisTemplate.opsForValue().set(key, HelperUtil.GSON.toJson(loginData), Duration.ofSeconds(DateUtil.zerodhaTokenExpiry()));
            this.setConfig(loginData.getJwtToken(), loginData.getFeedToken(),
                    mongoConfigService.getConfig().getAngelOneConfig().getApiKey(),
                    mongoConfigService.getConfig().getAngelOneConfig().getClientId());
        }
    }

    @Override
    public void disconnect() {
        connected.set(false);

        synchronized (this) {
            if (session != null && session.isOpen()) {
                try {
                    session.close(CloseStatus.NORMAL);
                    log.info("AngelOne WebSocket connection closed gracefully");
                } catch (IOException e) {
                    log.error("Error while closing WebSocket session", e);
                } finally {
                    session = null;
                    scheduler.shutdownNow();
                }
            }
        }

        ltpCache.clear();
    }

    @PreDestroy
    public void tearDown() {
        disconnect();
    }

}