package com.app.shahbaztrades.components.observer;

import com.app.shahbaztrades.model.dto.order.ActiveMtfTrade;
import com.app.shahbaztrades.model.dto.strategy.ActiveTrade;
import com.app.shahbaztrades.model.dto.strategy.TradeCompletionEvent;
import com.app.shahbaztrades.service.AngelOneService;
import com.app.shahbaztrades.util.Cache;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import com.google.common.util.concurrent.Striped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeWatchdog {

    private final Cache<String, List<ActiveTrade>> tradeWatchCache = new Cache<>();
    private final Cache<String, List<ActiveMtfTrade>> mtfTradeWatchCache = new Cache<>();
    private final AngelOneService angelOneService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Striped<Lock> tokenLocks = Striped.lock(8192);
    private final Striped<Lock> mtfTokenLocks = Striped.lock(8192);

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

    public void unwatch(ActiveTrade trade) {
        Lock lock = tokenLocks.get(trade.getToken());
        lock.lock();
        try {
            List<ActiveTrade> trades = tradeWatchCache.get(trade.getToken());
            if (CollectionUtils.isEmpty(trades)) return;
            trades.remove(trade);
        } finally {
            lock.unlock();
        }
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

        if (CollectionUtils.isEmpty(activeKeys)) {
            return;
        }

        for (String activeKey : activeKeys) {
            HelperUtil.EXECUTOR.execute(() -> processActiveKey(activeKey));
        }
    }

    private void processActiveKey(String activeKey) {
        Lock lock = tokenLocks.get(activeKey);
        lock.lock();
        try {
            double ltp = angelOneService.getLTP(activeKey);
            if (ltp <= 0) return;
            List<ActiveTrade> trades = tradeWatchCache.get(activeKey);
            if (CollectionUtils.isEmpty(trades)) return;
            trades.forEach(trade -> {
                if (ltp >= trade.getTargetPrice()) {
                    applicationEventPublisher.publishEvent(new TradeCompletionEvent(trade.getUserId(), trade));
                }
            });
        } finally {
            lock.unlock();
        }
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.SECONDS)
    public void executeMtfPulse() {
        var activeKeys = mtfTradeWatchCache.getActiveKeys();
        if (handleMtfSquareOff(activeKeys)) {
            return;
        }

        if (CollectionUtils.isEmpty(activeKeys)) {
            return;
        }

        for (String activeKey : activeKeys) {
            HelperUtil.EXECUTOR.execute(() -> processMtfActiveKey(activeKey));
        }
    }

    private boolean handleMtfSquareOff(Set<String> activeKeys) {
        if (DateUtil.isSquareOffTimeReached()) {
            if (!activeKeys.isEmpty()) {
                log.info("Market session over. Purging mtf watchdog cache.");
                mtfTradeWatchCache.invalidateAll();
            }
            return true;
        }
        return false;
    }

    private void processMtfActiveKey(String activeKey) {
        Lock lock = mtfTokenLocks.get(activeKey);
        lock.lock();
        try {
            double ltp = angelOneService.getLTP(activeKey);
            if (ltp <= 0) return;
            List<ActiveMtfTrade> trades = mtfTradeWatchCache.get(activeKey);
            if (CollectionUtils.isEmpty(trades)) return;
            trades.forEach(trade -> {
                if (ltp != trade.getPrevLtp()) {
                    trade.setPrevLtp(ltp);
                    trade.setLtp(ltp);
                    if (ltp > trade.getPeakPrice()) {
                        trade.setPeakPrice((float) ltp);
                    }
                    applicationEventPublisher.publishEvent(trade);
                }
            });
        } finally {
            lock.unlock();
        }
    }

    public void watchMtfTrade(ActiveMtfTrade trade) {
        if (DateUtil.isSquareOffTimeReached())
            return;

        var token = trade.getOrder().getMargin().getToken();
        Lock lock = mtfTokenLocks.get(token);
        lock.lock();
        try {
            List<ActiveMtfTrade> trades = mtfTradeWatchCache.get(token);
            if (trades == null) {
                trades = new CopyOnWriteArrayList<>();
                trades.add(trade);
                Duration ttl = DateUtil.getDurationUntilMarketClose();
                mtfTradeWatchCache.set(token, trades, ttl);
            } else {
                trades.add(trade);
            }
        } finally {
            lock.unlock();
        }

        log.info("Watchdog: Added {} for user {} for Mtf Trade: {}",
                trade.getOrder().getMargin().getSymbol(), trade.getOrder().getUserId(), trade.getOrder().getId());
    }

    public void unwatchMtfTrade(ActiveMtfTrade trade) {
        var token = trade.getOrder().getMargin().getToken();
        Lock lock = mtfTokenLocks.get(token);
        lock.lock();
        try {
            List<ActiveMtfTrade> trades = mtfTradeWatchCache.get(token);
            if (CollectionUtils.isEmpty(trades)) return;
            trades.remove(trade);
        } finally {
            lock.unlock();
        }
    }

}
