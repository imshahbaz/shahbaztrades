package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.observer.TradeWatchdog;
import com.app.shahbaztrades.components.orderrouting.OrderRouterFactory;
import com.app.shahbaztrades.model.dto.chartink.ChartInkBacktestMarginDto;
import com.app.shahbaztrades.model.dto.chartink.ChartInkSignalEvent;
import com.app.shahbaztrades.model.dto.fcm.NotificationRequest;
import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.dto.strategy.ActiveTrade;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import com.app.shahbaztrades.model.dto.strategy.TargetStockResult;
import com.app.shahbaztrades.model.dto.strategy.TradeCompletionEvent;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.entity.StrategyOrder;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.service.*;
import com.app.shahbaztrades.util.Cache;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import com.zerodhatech.kiteconnect.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeEngineImpl implements TradeEngine {

    private final Map<String, ScheduledFuture<?>> runningPollers = new ConcurrentHashMap<>();
    private final Cache<String, Boolean> activeOrders = new Cache<>();
    private final Cache<String, List<StrategyOrder>> strategyOrders = new Cache<>();

    private final StrategyOrderService strategyOrderService;
    private final StrategyService strategyService;
    private final ChartInkService chartInkService;
    private final ApplicationEventPublisher eventPublisher;
    private final AngelOneService angelOneService;
    private final TradeWatchdog tradeWatchdog;
    private final OrderRouterFactory orderRouterFactory;

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
        var strategy = strategyService.getCachedStrategies().get(strategyName);

        if (strategy == null) {
            log.error("Strategy configuration not found for {}", strategyName);
            return;
        }

        var list = strategyOrders.get(strategyName);
        if (list == null) {
            list = new CopyOnWriteArrayList<>();
            list.add(order);
        } else {
            list.add(order);
        }

        strategyOrders.set(strategyName, list, DateUtil.getDurationUntilMarketClose());

        if (processedStrategies.contains(strategyName)) {
            return;
        }

        processedStrategies.add(strategyName);
        HelperUtil.EXECUTOR.execute(() -> startManualPoller(strategy));
    }

    public void startManualPoller(StrategyDto strategy) {
        runningPollers.computeIfAbsent(strategy.getName(), name -> {
            log.info("Manual Watchdog Poller started for strategy {}", name);
            return HelperUtil.SCHEDULER.scheduleAtFixedRate(() -> HelperUtil.EXECUTOR.execute(() -> runPollerTask(name)), 1, 1, TimeUnit.MINUTES);
        });
    }

    private void runPollerTask(String name) {
        if (DateUtil.isSquareOffTimeReached()) {
            log.info("Market closed. Watchdog exiting.");
            var task = runningPollers.remove(name);
            if (task != null) {
                task.cancel(false);
            }
            return;
        }

        String currentTime = LocalTime.now(DateUtil.IST_ZONE).format(HOUR_MIN_FORMATTER);

        if (FIFTEEN_MIN_TARGETS.contains(currentTime)) {
            log.info("Target match at time {} ! Fetching signals...", currentTime);
            try {
                var signals = chartInkService.fetchTodayBacktestDataWithMargin(name);
                if (CollectionUtils.isEmpty(signals)) {
                    return;
                }

                log.info("Complete signals list: {}", signals);
                eventPublisher.publishEvent(new ChartInkSignalEvent(name, signals));
            } catch (Exception e) {
                log.error("Manual fetch failed", e);
            }
        }
    }

    @EventListener
    @Async("taskExecutor")
    public void chartInkSignalListener(ChartInkSignalEvent event) {
        var list = strategyOrders.get(event.strategyName());
        if (CollectionUtils.isEmpty(list)) {
            return;
        }

        for (var order : list) {
            HelperUtil.EXECUTOR.execute(() -> processSignalForOrder(order, event.signals()));
        }
    }

    private void processSignalForOrder(StrategyOrder order, List<ChartInkBacktestMarginDto> signals) {
        Boolean active = activeOrders.get(order.getId());
        if (active != null && active)
            return;

        var targetStock = findTargetStock(signals, order.getAmount(), order.getBroker());
        if (targetStock == null)
            return;

        punchSingleTrade(targetStock.margin(), targetStock.qty(), order.getUserId(), order);
    }

    private TargetStockResult findTargetStock(List<ChartInkBacktestMarginDto> signals, float orderAmount, BrokerType brokerType) {
        var now = DateUtil.getCurrentDateTime();
        var latest = signals.getLast();
        if (now.isAfter(latest.getMarketTime().plusMinutes(20)) && now.isBefore(latest.getMarketTime().plusMinutes(23)) && !CollectionUtils.isEmpty(latest.getMargins())) {
            return processSignal(latest, orderAmount, brokerType);
        }
        return null;
    }

    private TargetStockResult processSignal(ChartInkBacktestMarginDto signal, float orderAmount, BrokerType brokerType) {
        try {
            List<Margin> targetList;
            if (brokerType.equals(BrokerType.ZERODHA) || signal.getMargins().size() <= 1) {
                targetList = signal.getMargins();
            } else {
                targetList = signal.getMargins().stream().sorted(Comparator.comparingDouble(Margin::getRupeezyMargin).reversed())
                        .toList();
            }

            for (var margin : targetList) {
                var target = processTargetMargin(margin, orderAmount);
                if (target != null) {
                    return target;
                }
            }
        } catch (InterruptedException e) {
            log.error("Interrupted processing signal", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error processing signal", e);
        }

        return null;
    }

    private TargetStockResult processTargetMargin(Margin target, float orderAmount) throws Exception {
        var ltp = angelOneService.getLTP(target.getToken());
        if (ltp == -2) {
            return null;
        }

        if (ltp == -1) {
            angelOneService.subscribe(target.getToken(), ExchangeType.NSE.getValue());
            Thread.sleep(1000);
            ltp = angelOneService.getLTP(target.getToken());
            if (ltp < 0) {
                return null;
            }
        }

        int quantity = (int) (orderAmount / target.getRequiredMargin());
        if (quantity > 0) {
            return new TargetStockResult(target, quantity);
        }

        return null;
    }

    private void punchSingleTrade(Margin targetStock, int qty, long userId, StrategyOrder order) {
        log.info("Initiating trade for User: {} | Symbol: {} | Qty: {}", userId, targetStock.getSymbol(), qty);

        try {
            var orderRouter = orderRouterFactory.getRouter(order.getBroker());

            var req = TradeOrderRequest.builder().symbol(targetStock.getSymbol()).quantity(qty)
                    .transactionType(Constants.TRANSACTION_TYPE_BUY).orderType(Constants.ORDER_TYPE_MARKET).build();

            var orderResp = orderRouter.placeMTFOrder(order.getUserId(), req);

            HelperUtil.pollWait(1000);

            var orderDetails = orderRouter.getOrderDetails(order.getUserId(), orderResp.getOrderId());
            double entryPrice = orderDetails.getAveragePrice();

            double targetPrice = HelperUtil.fixToTick(entryPrice * 1.004);

            log.info("Entry Executed at: {} | Target Set at: {}", entryPrice, targetPrice);

            req = TradeOrderRequest.builder().symbol(targetStock.getSymbol()).quantity(qty).price(targetPrice)
                    .transactionType(Constants.TRANSACTION_TYPE_SELL).orderType(Constants.ORDER_TYPE_LIMIT).build();

            var exitResp = orderRouter.placeMTFOrder(order.getUserId(), req);

            tradeWatchdog.watch(ActiveTrade.builder()
                    .userId(userId)
                    .strategyOrderId(order.getId())
                    .symbol(targetStock.getSymbol())
                    .token(targetStock.getToken())
                    .quantity(qty)
                    .entryPrice(entryPrice)
                    .targetPrice(targetPrice)
                    .exitOrderId(exitResp.getOrderId())
                    .broker(order.getBroker())
                    .build());

            eventPublisher.publishEvent(new NotificationRequest(
                    userId,
                    "Trade Active",
                    String.format("Bought %s at %.2f. Target: %.2f", targetStock.getSymbol(), entryPrice, targetPrice),
                    Collections.emptyMap()
            ));

            activeOrders.set(order.getId(), Boolean.TRUE, DateUtil.getDurationUntilMarketClose());

        } catch (Exception e) {
            log.error("Error in punchSingleTrade for {}", targetStock.getSymbol(), e);
        }
    }

    @EventListener
    @Async("taskExecutor")
    public void tradeCompletionListener(TradeCompletionEvent event) throws Exception {
        var orderRouter = orderRouterFactory.getRouter(event.trade().getBroker());
        var det = orderRouter.getOrderDetails(event.userId(), event.trade().getExitOrderId());
        if (det.getPendingQuantity() == 0) {
            log.info("Exit order filled for {}", event.trade().getSymbol());
            eventPublisher.publishEvent(new NotificationRequest(
                    event.userId(),
                    "Target Achieved!",
                    String.format("%s | Target: %.2f", event.trade().getSymbol(), event.trade().getTargetPrice()),
                    Collections.emptyMap()
            ));

            activeOrders.remove(event.trade().getStrategyOrderId());
        }
    }

}
