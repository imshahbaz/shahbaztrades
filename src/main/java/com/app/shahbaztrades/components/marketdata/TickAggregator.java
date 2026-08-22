package com.app.shahbaztrades.components.marketdata;

import com.app.shahbaztrades.model.dto.angelone.websocket.LiveTick;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.service.impl.StrategyRegistry;
import com.app.shahbaztrades.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Turns the raw tick stream into bars. Each watched token gets one virtual thread draining its own
 * queue, so a slow token cannot stall the websocket reader or any other token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TickAggregator {

    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final long POLL_TIMEOUT_SECONDS = 1;
    private static final int BAR_MINUTES = 15;

    private final ConcurrentHashMap<String, BlockingQueue<LiveTick>> tokenTickBufferMap = new ConcurrentHashMap<>();
    private final Set<String> activeWorkers = ConcurrentHashMap.newKeySet();
    private final BarSeriesStore barSeriesStore;
    private final StrategyRegistry strategyRegistry;
    private final AsyncTaskExecutor taskExecutor;

    /**
     * Hands a tick to the token's worker. Dropped when no worker is running for that token, which is
     * the normal case for tokens watched only for their price and not for bars.
     */
    public void accept(String token, double ltp) {
        if (!activeWorkers.contains(token)) {
            return;
        }
        bufferFor(token).add(new LiveTick(ltp, ZonedDateTime.now(DateUtil.IST_ZONE)));
    }

    /**
     * Subscribes every token the registry is tracking and starts a worker per token.
     *
     * @param webSocketSubscriber the feed's subscribe call, passed in rather than injected so the
     *                            feed can depend on this aggregator without a cycle.
     */
    @Async("taskExecutor")
    public void startWorkersForActiveWatchlist(BiConsumer<String, Integer> webSocketSubscriber) {
        var activeTokens = strategyRegistry.getAllActiveTokens();

        for (String token : activeTokens) {
            webSocketSubscriber.accept(token, ExchangeType.NSE.getValue());
            if (activeWorkers.add(token)) {
                taskExecutor.execute(() -> runTokenEventLoop(token));
            }
        }
    }

    public void clear() {
        tokenTickBufferMap.clear();
        activeWorkers.clear();
    }

    private BlockingQueue<LiveTick> bufferFor(String token) {
        return tokenTickBufferMap.computeIfAbsent(token, _ -> new LinkedBlockingQueue<>());
    }

    private void runTokenEventLoop(String token) {
        BlockingQueue<LiveTick> queue = bufferFor(token);

        log.info("🚀 Started dedicated Virtual Thread loop for token: {}", token);

        while (!DateUtil.isMarketClosedForTrading()) {
            LiveTick tick = pollNextTick(queue);
            if (tick == null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                continue;
            }
            processTick(token, tick);
        }

        activeWorkers.remove(token);
        log.info("🛑 Stopped worker loop for token: {}", token);
    }

    private LiveTick pollNextTick(BlockingQueue<LiveTick> queue) {
        try {
            return queue.poll(POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void processTick(String token, LiveTick tick) {
        ZonedDateTime tickTimeIST = tick.arrivalTime();
        // Pre-open ticks belong to no bar; folding them in would corrupt the 9:15 open.
        if (tickTimeIST.getHour() == MARKET_OPEN.getHour() && tickTimeIST.getMinute() < MARKET_OPEN.getMinute()) {
            return;
        }

        barSeriesStore.applyTick(token, tick.price(), computeBarEndTime(tickTimeIST));
    }

    private Instant computeBarEndTime(ZonedDateTime tickTimeIST) {
        int startMinute = (tickTimeIST.getMinute() / BAR_MINUTES) * BAR_MINUTES;
        return tickTimeIST.truncatedTo(ChronoUnit.HOURS)
                .withMinute(startMinute)
                .plusMinutes(BAR_MINUTES)
                .toInstant();
    }
}
