package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.helper.PollingHelper;
import com.app.shahbaztrades.components.observer.TradeWatchdog;
import com.app.shahbaztrades.components.trading.ContinuousTradeExecutor;
import com.app.shahbaztrades.components.trading.TradeCandidateSelector;
import com.app.shahbaztrades.model.dto.chartink.ChartInkBacktestMarginDto;
import com.app.shahbaztrades.model.dto.chartink.ChartInkSignalEvent;
import com.app.shahbaztrades.model.dto.strategy.TradeCompletionEvent;
import com.app.shahbaztrades.model.entity.StrategyOrder;
import com.app.shahbaztrades.model.enums.OrderStatus;
import com.app.shahbaztrades.service.StrategyOrderService;
import com.app.shahbaztrades.service.StrategyService;
import com.app.shahbaztrades.service.TradeEngine;
import com.app.shahbaztrades.util.Cache;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Drives continuous trading: buckets the day's orders by strategy, matches incoming ChartInk
 * signals to them, and holds the in-flight guard that stops one order being traded twice.
 * <p>
 * Choosing what to buy is {@link TradeCandidateSelector}'s job; placing and settling it is
 * {@link ContinuousTradeExecutor}'s.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeEngineImpl implements TradeEngine {

    /** A signal is only actionable between these offsets from its own market time. */
    private static final int SIGNAL_WINDOW_OPEN_MINUTES = 15;
    private static final int SIGNAL_WINDOW_CLOSE_MINUTES = 23;

    /** Orders currently being traded, keyed by strategy-order id. Guards against duplicate signals. */
    private final Cache<String, Boolean> activeOrders = new Cache<>();
    private final Cache<String, List<StrategyOrder>> strategyOrders = new Cache<>();
    private final StrategyOrderService strategyOrderService;
    private final StrategyService strategyService;
    private final ApplicationEventPublisher eventPublisher;
    private final TradeWatchdog tradeWatchdog;
    private final PollingHelper pollingHelper;
    private final TradeCandidateSelector tradeCandidateSelector;
    private final ContinuousTradeExecutor continuousTradeExecutor;

    @Override
    public void continuousTrade() {
        var orders = strategyOrderService.getTodayOrders();
        if (CollectionUtils.isEmpty(orders)) {
            log.info("No orders found for continuous trading");
            return;
        }

        Set<String> processedStrategies = new HashSet<>();

        for (var order : orders) {
            processStrategyOrder(order, processedStrategies);
        }
    }

    private void processStrategyOrder(StrategyOrder order, Set<String> processedStrategies) {
        String strategyName = order.getStrategyName();
        var strategy = strategyService.getCachedStrategies()
                .get(strategyName == null ? null : strategyName.toUpperCase());

        if (strategy == null) {
            log.error("Strategy configuration not found for {}", strategyName);
            order.setOrderStatus(OrderStatus.REJECTED);
            eventPublisher.publishEvent(order);
            return;
        }

        var list = strategyOrders.get(strategyName);
        if (list == null) {
            list = new CopyOnWriteArrayList<>();
        }
        list.add(order);

        strategyOrders.set(strategyName, list, DateUtil.getDurationUntilMarketClose());

        order.setOrderStatus(OrderStatus.COMPLETED);
        eventPublisher.publishEvent(order);

        // One poller per strategy, however many orders subscribe to it.
        if (processedStrategies.add(strategyName)) {
            HelperUtil.EXECUTOR.execute(() -> pollingHelper.runPollerTask(strategyName, false));
        }
    }

    @EventListener
    @Async("taskExecutor")
    public void chartInkSignalListener(ChartInkSignalEvent event) {
        var list = strategyOrders.get(event.strategyName());
        if (CollectionUtils.isEmpty(list)) {
            return;
        }

        var now = DateUtil.getCurrentDateTime();
        var matched = mostRecentActionableSignal(event, now);
        if (matched == null || CollectionUtils.isEmpty(matched.getMargins())) {
            log.info("No signal found for strategy {} at {}", event.strategyName(), now);
            return;
        }

        for (var order : list) {
            HelperUtil.EXECUTOR.execute(() -> processSignalForOrder(order, matched));
        }
    }

    /** Scans newest first: an older signal is only used when nothing fresher is in the window. */
    private ChartInkBacktestMarginDto mostRecentActionableSignal(ChartInkSignalEvent event, LocalDateTime now) {
        for (int i = event.signals().size() - 1; i >= 0; i--) {
            var signal = event.signals().get(i);
            if (now.isAfter(signal.getMarketTime().plusMinutes(SIGNAL_WINDOW_OPEN_MINUTES))
                    && now.isBefore(signal.getMarketTime().plusMinutes(SIGNAL_WINDOW_CLOSE_MINUTES))) {
                return signal;
            }
        }
        return null;
    }

    private void processSignalForOrder(StrategyOrder order, ChartInkBacktestMarginDto signal) {
        Boolean active = activeOrders.get(order.getId());
        if (active != null && active) {
            return;
        }

        var targetStock = tradeCandidateSelector.select(signal, order.getAmount(), order.getBroker());
        if (targetStock == null) {
            return;
        }

        // Sizing can take a second, so the real claim happens here, not at the check above.
        if (!activeOrders.setIfAbsent(order.getId(), Boolean.TRUE, DateUtil.getDurationUntilMarketClose())) {
            log.info("Order {} already being processed, skipping duplicate signal", order.getId());
            return;
        }

        if (!continuousTradeExecutor.openTrade(order, targetStock.margin(), targetStock.qty())) {
            activeOrders.remove(order.getId());
        }
    }

    @EventListener
    @Async("taskExecutor")
    public void tradeCompletionListener(TradeCompletionEvent event) {
        try {
            if (continuousTradeExecutor.closeIfFilled(event)) {
                activeOrders.remove(event.trade().getStrategyOrderId());
            }
        } finally {
            tradeWatchdog.clearTrigger(event.trade());
        }
    }
}
