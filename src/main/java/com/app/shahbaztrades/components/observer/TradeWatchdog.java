package com.app.shahbaztrades.components.observer;

import com.app.shahbaztrades.model.dto.order.ActiveMtfTrade;
import com.app.shahbaztrades.model.dto.order.MtfTickEvent;
import com.app.shahbaztrades.model.dto.strategy.ActiveTrade;
import com.app.shahbaztrades.model.dto.strategy.TradeCompletionEvent;
import com.app.shahbaztrades.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Turns the tick stream into trade events.
 * <p>
 * Two kinds of position are watched and they react to a tick differently — a continuous trade fires
 * once when it reaches its target, an MTF position reports every price change so its trail can be
 * re-evaluated. Only those two reactions live here; all the registry bookkeeping is
 * {@link WatchRegistry}'s, held once per trade type.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeWatchdog {

    private final WatchRegistry<ActiveTrade> targetWatches =
            new WatchRegistry<>(ActiveTrade::getToken, ActiveTrade::getStrategyOrderId);
    private final WatchRegistry<ActiveMtfTrade> mtfWatches =
            new WatchRegistry<>(trade -> trade.getOrder().getMargin().getToken(),
                    trade -> trade.getOrder().getId());
    private final ApplicationEventPublisher applicationEventPublisher;

    // --- continuous trades: fire once on reaching target ------------------

    public void watch(ActiveTrade trade) {
        if (!targetWatches.watch(trade)) {
            return;
        }
        log.info("Watchdog: Added {} for user {}. Target: {}",
                trade.getSymbol(), trade.getUserId(), trade.getTargetPrice());
    }

    public void unwatch(ActiveTrade trade) {
        targetWatches.unwatch(trade);
    }

    public void clearTrigger(ActiveTrade trade) {
        targetWatches.release(trade);
    }

    // --- MTF positions: report every price change -------------------------

    public void watchMtfTrade(ActiveMtfTrade trade) {
        if (!mtfWatches.watch(trade)) {
            return;
        }
        log.info("Watchdog: Added {} for user {} for Mtf Trade: {}",
                trade.getOrder().getMargin().getSymbol(), trade.getOrder().getUserId(), trade.getOrder().getId());
    }

    public void unwatchMtfTrade(ActiveMtfTrade trade) {
        mtfWatches.unwatch(trade);
    }

    public void clearMtfTrigger(ActiveMtfTrade trade) {
        mtfWatches.release(trade);
    }

    // --- tick handling ----------------------------------------------------

    public void onTick(String token, double ltp) {
        if (ltp <= 0) {
            return;
        }

        checkTargetHits(token, ltp);
        checkMtfTicks(token, ltp);
    }

    private void checkTargetHits(String token, double ltp) {
        for (ActiveTrade trade : targetWatches.watching(token)) {
            if (ltp >= trade.getTargetPrice() && targetWatches.claim(trade)) {
                applicationEventPublisher.publishEvent(new TradeCompletionEvent(trade.getUserId(), trade));
            }
        }
    }

    private void checkMtfTicks(String token, double ltp) {
        for (ActiveMtfTrade trade : mtfWatches.watching(token)) {
            if (ltp == trade.getPrevLtp()) {
                continue;
            }

            if (ltp > trade.getPeakPrice()) {
                trade.setPeakPrice(ltp);
            }

            if (mtfWatches.claim(trade)) {
                trade.setPrevLtp(ltp);
                trade.setLtp(ltp);
                applicationEventPublisher.publishEvent(new MtfTickEvent(trade, ltp, trade.getPeakPrice()));
            }
        }
    }

    // --- monitoring -------------------------------------------------------

    public int getWatchedTokenCount() {
        return targetWatches.watchedTokenCount();
    }

    public int getWatchedTradeCount() {
        return targetWatches.watchedTradeCount();
    }

    public int getMtfWatchedTokenCount() {
        return mtfWatches.watchedTokenCount();
    }

    public int getMtfWatchedTradeCount() {
        return mtfWatches.watchedTradeCount();
    }

    public int getInFlightTriggerCount() {
        return targetWatches.inFlightCount();
    }

    public int getInFlightMtfTriggerCount() {
        return mtfWatches.inFlightCount();
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.SECONDS)
    public void purgeAtSquareOff() {
        if (!DateUtil.isSquareOffTimeReached()) {
            return;
        }

        if (targetWatches.purge()) {
            log.info("Market session over. Purging watchdog cache.");
        }

        if (mtfWatches.purge()) {
            log.info("Market session over. Purging mtf watchdog cache.");
        }
    }
}
