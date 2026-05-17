package com.app.shahbaztrades.components.observer;

import com.app.shahbaztrades.model.dto.strategy.ActiveTrade;
import com.app.shahbaztrades.model.dto.strategy.TradeCompletionEvent;
import com.app.shahbaztrades.service.AngelOneService;
import com.app.shahbaztrades.util.Cache;
import com.app.shahbaztrades.util.DateUtil;
import com.google.common.util.concurrent.Striped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeWatchdog {

    private final Cache<String, List<ActiveTrade>> tradeWatchCache = new Cache<>();
    private final AngelOneService angelOneService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Striped<Lock> tokenLocks = Striped.lock(2048);

    public void watch(ActiveTrade trade) {
        if (DateUtil.isSquareOffTimeReached())
            return;

        Lock lock = tokenLocks.get(trade.getToken());
        lock.lock();
        try {
            List<ActiveTrade> trades = tradeWatchCache.get(trade.getToken());
            if (trades == null) {
                trades = new CopyOnWriteArrayList<>();
                trades.add(trade);
                Duration ttl = DateUtil.getDurationUntilMarketClose();
                tradeWatchCache.set(trade.getToken(), trades, ttl);
            } else {
                trades.add(trade);
            }
        } finally {
            lock.unlock();
        }

        log.info("Watchdog: Added {} for user {}. Target: {}",
                trade.getSymbol(), trade.getUserId(), trade.getTargetPrice());
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.SECONDS)
    public void executePulse() {
        var activeKeys = tradeWatchCache.getActiveKeys();
        if (DateUtil.isSquareOffTimeReached()) {
            if (!activeKeys.isEmpty()) {
                log.info("Market session over. Purging watchdog cache.");
                tradeWatchCache.invalidateAll();
            }
            return;
        }

        if (activeKeys.isEmpty()) {
            return;
        }

        for (String activeKey : activeKeys) {
            double ltp = angelOneService.getLTP(activeKey);
            if (ltp <= 0) continue;
            List<ActiveTrade> trades = tradeWatchCache.get(activeKey);
            if (CollectionUtils.isEmpty(trades)) continue;
            trades.removeIf(trade -> {
                if (ltp >= trade.getTargetPrice()) {
                    applicationEventPublisher.publishEvent(new TradeCompletionEvent(trade.getUserId(), trade));
                    return true;
                }
                return false;
            });

            if (trades.isEmpty()) {
                tradeWatchCache.remove(activeKey);
            }
        }
    }

}
