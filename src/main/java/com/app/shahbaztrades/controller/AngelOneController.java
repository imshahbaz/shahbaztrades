package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.angelone.websocket.AngelOneWsSubscribeDto;
import com.app.shahbaztrades.service.AngelOneWebSocketService;
import com.app.shahbaztrades.util.HelperUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/angelone")
public class AngelOneController {

    private final AngelOneWebSocketService angelOneWebSocketService;

    @PublicEndpoint
    @PostMapping("/refresh-session")
    public void refreshSession() {
        angelOneWebSocketService.refreshBrokerSession();
    }

    @PublicEndpoint
    @PostMapping("/ws/connect")
    public void connect() {
        angelOneWebSocketService.startWebSocket();
    }

    @PublicEndpoint
    @PostMapping("/ws/disconnect")
    public void disconnect() {
        angelOneWebSocketService.disconnect();
    }

    @PublicEndpoint
    @PostMapping("/ws/subscribe")
    public void subscribe(@RequestBody @Valid AngelOneWsSubscribeDto request) {
        for (String token : request.getTokens()) {
            try {
                angelOneWebSocketService.subscribe(token, request.getExchangeType());
                startMonitoring(token);
            } catch (Exception e) {
                log.error("Failed to subscribe to token: {}", token, e);
            }
        }
    }

    private void startMonitoring(String token) {
        CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            long timeoutMillis = 30 * 1000; // 30 Seconds timeout for the loop

            while (System.currentTimeMillis() - startTime < timeoutMillis) {
                double ltp = angelOneWebSocketService.getLTP(token);

                if (ltp == -2) {
                    log.warn("Monitor stopping for {}: WebSocket connection lost", token);
                    return;
                }

                if (ltp > 0) {
                    log.info("LTP for {}: {}", token, ltp);
                }

                if (!HelperUtil.pollWait(200)) {
                    log.info("Monitor for {} cancelled", token);
                    return;
                }
            }
            log.info("Monitor for {} finished after 30 seconds", token);
        }, HelperUtil.EXECUTOR);
    }

}
