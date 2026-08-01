package com.app.shahbaztrades.components.observer;

import com.app.shahbaztrades.model.dto.order.ActiveMtfTrade;
import com.app.shahbaztrades.model.dto.order.MtfTickEvent;
import com.app.shahbaztrades.model.dto.strategy.ActiveTrade;
import com.app.shahbaztrades.model.dto.strategy.TradeCompletionEvent;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeWatchdog {

    private final Cache<String, List<ActiveTrade>> tradeWatchCache = new Cache<>();
    private final Cache<String, List<ActiveMtfTrade>> mtfTradeWatchCache = new Cache<>();
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Striped<Lock> tokenLocks = Striped.lock(8192);
    private final Striped<Lock> mtfTokenLocks = Striped.lock(8192);
    private final Set<String> triggeredTrades = ConcurrentHashMap.newKeySet();
    private final Set<String> triggeredMtfTrades = ConcurrentHashMap.newKeySet();

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

    public void clearTrigger(ActiveTrade trade) {
        triggeredTrades.remove(trade.getStrategyOrderId());
    }

    public void clearMtfTrigger(ActiveMtfTrade trade) {
        triggeredMtfTrades.remove(trade.getOrder().getId());
    }

    public void onTick(String token, double ltp) {
        if (ltp <= 0) {
            return;
        }

        checkTargetHits(token, ltp);
        checkMtfTicks(token, ltp);
    }

    private void checkTargetHits(String token, double ltp) {
        List<ActiveTrade> trades = tradeWatchCache.get(token);
        if (CollectionUtils.isEmpty(trades)) {
            return;
        }

        for (ActiveTrade trade : trades) {
            if (ltp >= trade.getTargetPrice() && triggeredTrades.add(trade.getStrategyOrderId())) {
                applicationEventPublisher.publishEvent(new TradeCompletionEvent(trade.getUserId(), trade));
            }
        }
    }

    private void checkMtfTicks(String token, double ltp) {
        List<ActiveMtfTrade> trades = mtfTradeWatchCache.get(token);
        if (CollectionUtils.isEmpty(trades)) {
            return;
        }

        for (ActiveMtfTrade trade : trades) {
            if (ltp != trade.getPrevLtp()) {
                if (ltp > trade.getPeakPrice()) {
                    trade.setPeakPrice(ltp);
                }

                if (triggeredMtfTrades.add(trade.getOrder().getId())) {
                    trade.setPrevLtp(ltp);
                    trade.setLtp(ltp);
                    applicationEventPublisher.publishEvent(new MtfTickEvent(trade, ltp, trade.getPeakPrice()));
                }
            }
        }
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.SECONDS)
    public void purgeAtSquareOff() {
        if (!DateUtil.isSquareOffTimeReached()) {
            return;
        }

        if (!tradeWatchCache.getActiveKeys().isEmpty()) {
            log.info("Market session over. Purging watchdog cache.");
            tradeWatchCache.invalidateAll();
            triggeredTrades.clear();
        }

        if (!mtfTradeWatchCache.getActiveKeys().isEmpty()) {
            log.info("Market session over. Purging mtf watchdog cache.");
            mtfTradeWatchCache.invalidateAll();
            triggeredMtfTrades.clear();
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
