package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;
import com.app.shahbaztrades.model.dto.angelone.websocket.AngelOneWsSubscribeDto;
import com.app.shahbaztrades.model.dto.angelone.websocket.Ltp;
import com.app.shahbaztrades.service.BrokerSession;
import com.app.shahbaztrades.service.MarketDataQuery;
import com.app.shahbaztrades.service.MarketFeed;
import com.app.shahbaztrades.service.MarketFeedAdmin;
import com.app.shahbaztrades.util.HelperUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/angelone")
public class AngelOneController {

    private final MarketFeed marketFeed;
    private final MarketFeedAdmin marketFeedAdmin;
    private final MarketDataQuery marketDataQuery;
    private final BrokerSession brokerSession;

    @PublicEndpoint
    @PostMapping("/refresh-session")
    public void refreshSession() {
        brokerSession.refreshBrokerSession();
    }

    @PublicEndpoint
    @PostMapping("/ws/connect")
    public void connect() {
        marketFeedAdmin.start();
    }

    @PublicEndpoint
    @PostMapping("/ws/disconnect")
    public void disconnect() {
        marketFeedAdmin.disconnect();
    }

    @PublicEndpoint
    @PostMapping("/ws/subscribe")
    public void subscribe(@RequestBody @Valid AngelOneWsSubscribeDto request) {
        for (String token : request.getTokens()) {
            try {
                marketFeed.subscribe(token, request.getExchangeType());
                startMonitoring(token);
            } catch (Exception e) {
                log.error("Failed to subscribe to token: {}", token, e);
            }
        }
    }

    private void startMonitoring(String token) {
        CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            long timeoutMillis = 30 * 1000L; // 30 Seconds timeout for the loop

            while (System.currentTimeMillis() - startTime < timeoutMillis) {
                switch (marketFeed.getLtp(token)) {
                    case Ltp.FeedDown _ -> {
                        log.warn("Monitor stopping for {}: WebSocket connection lost", token);
                        return;
                    }
                    case Ltp.Price(double value) -> log.info("LTP for {}: {}", token, value);
                    case Ltp.NotSubscribed _ -> log.trace("No tick yet for {}", token);
                }

                if (!HelperUtil.pollWait(200)) {
                    log.info("Monitor for {} cancelled", token);
                    return;
                }
            }
            log.info("Monitor for {} finished after 30 seconds", token);
        }, HelperUtil.EXECUTOR);
    }

    @PublicEndpoint
    @GetMapping("/ltp")
    public ResponseEntity<ApiResponse<SmartApiLtpResponse.MarketTicker>> getMultipleLtp(@RequestParam @NotBlank String token) {
        return ResponseEntity.ok(ApiResponse.ok(marketDataQuery.getMarketTicker(token), "Ltp Fetched Successfully"));
    }

}
