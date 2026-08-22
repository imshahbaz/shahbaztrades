package com.app.shahbaztrades.controller.admin;

import com.app.shahbaztrades.components.observer.MarketTickPipeline;
import com.app.shahbaztrades.components.observer.TradeWatchdog;
import com.app.shahbaztrades.config.security.AdminOnly;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.system.*;
import com.app.shahbaztrades.service.MarketFeedAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/server")
public class ServerMonitorController {

    private final MarketTickPipeline marketTickPipeline;
    private final TradeWatchdog tradeWatchdog;
    private final MarketFeedAdmin marketFeedAdmin;

    @AdminOnly
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ServerStats>> getMemoryStats() {
        return ResponseEntity.ok(ApiResponse.ok(ServerStats.snapshot(collectDomainStats()), "Server stats fetched successfully"));
    }

    private DomainStats collectDomainStats() {
        return new DomainStats(pipelineStats(), watchdogStats(), webSocketStats());
    }

    private PipelineStats pipelineStats() {
        long remaining = marketTickPipeline.getRemainingCapacity();
        long used = remaining < 0 ? -1 : marketTickPipeline.getRingBufferSize() - remaining;
        return new PipelineStats(marketTickPipeline.getRingBufferSize(), marketTickPipeline.getShardCount(), remaining, used);
    }

    private WatchdogStats watchdogStats() {
        return new WatchdogStats(
                tradeWatchdog.getWatchedTokenCount(),
                tradeWatchdog.getWatchedTradeCount(),
                tradeWatchdog.getMtfWatchedTokenCount(),
                tradeWatchdog.getMtfWatchedTradeCount(),
                tradeWatchdog.getInFlightTriggerCount(),
                tradeWatchdog.getInFlightMtfTriggerCount()
        );
    }

    private WebSocketStats webSocketStats() {
        return new WebSocketStats(marketFeedAdmin.isConnected(), marketFeedAdmin.getReconnectAttempts());
    }
}
