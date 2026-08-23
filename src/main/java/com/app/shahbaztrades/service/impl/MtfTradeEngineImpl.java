package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.strategy.DailyTradingStrategy;
import com.app.shahbaztrades.model.dto.analysis.TechnicalMetrics;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.service.MtfTradeEngine;
import com.app.shahbaztrades.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class MtfTradeEngineImpl implements MtfTradeEngine {

    private final OrderService orderService;
    private final DailyTradingStrategyRegistry dailyTradingStrategyRegistry;

    @Override
    @Async("taskExecutor")
    public void initiateMtfOrders() {
        // Shared across orders so one Yahoo lookup covers every order on the same symbol.
        Map<String, TechnicalMetrics> metrics = new ConcurrentHashMap<>();
        forEachTodayOrder("Initiate MTF", (strategy, order) -> strategy.initialiseTrade(order, metrics));
    }

    @Override
    public void updateMtfOrderStatus() {
        forEachTodayOrder("Update MTF Status", DailyTradingStrategy::updateTradeStatus);
    }

    @Override
    public void startTrading() {
        forEachTodayOrder("Start Trading", DailyTradingStrategy::startTrading);
    }

    /**
     * Runs one step of the daily run over today's orders. A failure is contained to its own order:
     * one broker rejection must not abandon everyone else's orders for the day.
     */
    private void forEachTodayOrder(String step, BiConsumer<DailyTradingStrategy, Order> action) {
        var orders = orderService.getTodayOrders();
        if (CollectionUtils.isEmpty(orders)) {
            log.info("No Orders found for today for {}", step);
            return;
        }

        for (var order : orders) {
            try {
                action.accept(dailyTradingStrategyRegistry.getStrategy(order.getStrategyName()), order);
            } catch (Exception e) {
                log.error("Error during {} for order {}", step, order.getId(), e);
            }
        }
    }
}
