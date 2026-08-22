package com.app.shahbaztrades.components.angelone;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.service.BrokerSession;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.annotation.PreDestroy;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.container.jdk.client.JdkClientContainer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static com.app.shahbaztrades.util.Constants.BEARER_PREFIX;

/**
 * Owns the AngelOne Smart Stream websocket: connection, auth headers, heartbeat, backoff and
 * reconnect. Knows nothing about what ticks mean — decoded frames go to the {@link Listener}.
 */
@Slf4j
@Component
public class SmartStreamConnection {

    private static final String WS_URL = "wss://smartapisocket.angelone.in/smart-stream";
    private static final long RECONNECT_MAX_DELAY_SECONDS = 30;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 20;
    private static final String PING = "ping";
    private static final String PONG = "pong";

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean intentionalDisconnect = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ReentrantLock wsLock = new ReentrantLock();
    private final JsonMapper jsonMapper;
    private final BrokerSession brokerSession;
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
    private volatile Listener listener;

    public SmartStreamConnection(JsonMapper jsonMapper, BrokerSession brokerSession) {
        this.jsonMapper = jsonMapper;
        this.brokerSession = brokerSession;
    }

    public boolean isConnected() {
        return connected.get();
    }

    public int getReconnectAttempts() {
        return reconnectAttempts.get();
    }

    /** No-op if already connected or the broker session has no JWT yet. */
    public void connect(Listener tickListener) {
        if (connected.get() || brokerSession.jwtToken() == null) return;

        this.listener = tickListener;
        intentionalDisconnect.set(false);
        reconnectAttempts.set(0);

        ClientManager client = ClientManager.createClient(JdkClientContainer.class.getName());
        client.getProperties().put(ClientProperties.RECONNECT_HANDLER, reconnectHandler);

        ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                .configurator(new AuthHeaderConfigurator())
                .build();

        try {
            client.connectToServer(new SmartStreamEndpoint(), config, URI.create(WS_URL));
        } catch (Exception e) {
            log.error("WebSocket Connection Error", e);
        }
    }

    public void send(Object payload) {
        Session local = session;
        if (local == null || !local.isOpen()) {
            throw new BadRequestException("Websocket is closed");
        }
        wsLock.lock();
        try {
            String json = jsonMapper.writeValueAsString(payload);
            log.info("Sending Subscription Request: {}", json);
            local.getBasicRemote().sendText(json);
        } catch (IOException e) {
            log.error("WebSocket Write Error", e);
        } finally {
            wsLock.unlock();
        }
    }

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
    }

    @PreDestroy
    public void tearDown() {
        disconnect();
        scheduler.shutdownNow();
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
                local.getBasicRemote().sendText(PING);
            } catch (IOException e) {
                log.error("Heartbeat failed", e);
            } finally {
                wsLock.unlock();
            }
        }
    }

    private void dispatch(ByteBuffer frame) {
        Listener local = listener;
        if (local == null) return;
        SmartStreamTickDecoder.decode(frame).ifPresent(local::onTick);
    }

    /** Callbacks into whatever is consuming the stream. */
    public interface Listener {

        void onTick(SmartStreamTickDecoder.Tick tick);

        /** Fired after a reconnect only, so subscriptions lost with the old session can be replayed. */
        void onReconnected();
    }

    private class AuthHeaderConfigurator extends ClientEndpointConfig.Configurator {
        @Override
        public void beforeRequest(Map<String, List<String>> headers) {
            headers.put("Authorization", List.of(BEARER_PREFIX + brokerSession.jwtToken()));
            headers.put("x-api-key", List.of(brokerSession.apiKey()));
            headers.put("x-client-code", List.of(brokerSession.clientId()));
            headers.put("x-feed-token", List.of(brokerSession.feedToken()));
        }
    }

    private class SmartStreamEndpoint extends Endpoint {
        @Override
        public void onOpen(Session sess, EndpointConfig config) {
            session = sess;
            connected.set(true);

            sess.addMessageHandler(ByteBuffer.class, SmartStreamConnection.this::dispatch);
            sess.addMessageHandler(String.class, msg -> {
                if (PONG.equals(msg)) {
                    log.trace("Received keep-alive pong");
                }
            });

            startHeartbeat();
            Listener local = listener;
            if (reconnectAttempts.get() > 0 && local != null) {
                local.onReconnected();
            }
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
