package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.helper.PollingHelper;
import com.app.shahbaztrades.components.observer.TradeWatchdog;
import com.app.shahbaztrades.components.orderrouting.OrderRouterFactory;
import com.app.shahbaztrades.model.dto.chartink.ChartInkBacktestMarginDto;
import com.app.shahbaztrades.model.dto.chartink.ChartInkSignalEvent;
import com.app.shahbaztrades.model.dto.fcm.NotificationRequest;
import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.dto.strategy.ActiveTrade;
import com.app.shahbaztrades.model.dto.strategy.TargetStockResult;
import com.app.shahbaztrades.model.dto.strategy.TradeCompletionEvent;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.entity.StrategyOrder;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.service.AngelOneService;
import com.app.shahbaztrades.service.StrategyOrderService;
import com.app.shahbaztrades.service.StrategyService;
import com.app.shahbaztrades.service.TradeEngine;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeEngineImpl implements TradeEngine {

    private static final int LTP_POLL_ATTEMPTS = 10;
    private static final long LTP_POLL_INTERVAL_MS = 100;
    private final Cache<String, Boolean> activeOrders = new Cache<>();
    private final Cache<String, List<StrategyOrder>> strategyOrders = new Cache<>();
    private final StrategyOrderService strategyOrderService;
    private final StrategyService strategyService;
    private final ApplicationEventPublisher eventPublisher;
    private final AngelOneService angelOneService;
    private final TradeWatchdog tradeWatchdog;
    private final OrderRouterFactory orderRouterFactory;
    private final PollingHelper pollingHelper;

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
        HelperUtil.EXECUTOR.execute(() -> pollingHelper.runPollerTask(strategyName, false));
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

        if (!activeOrders.setIfAbsent(order.getId(), Boolean.TRUE, DateUtil.getDurationUntilMarketClose())) {
            log.info("Order {} already being processed, skipping duplicate signal", order.getId());
            return;
        }

        boolean entryPlaced = punchSingleTrade(targetStock.margin(), targetStock.qty(), order.getUserId(), order);
        if (!entryPlaced) {
            activeOrders.remove(order.getId());
        }
    }

    private TargetStockResult findTargetStock(List<ChartInkBacktestMarginDto> signals, BigDecimal orderAmount, BrokerType brokerType) {
        var now = DateUtil.getCurrentDateTime();
        var latest = signals.getLast();
        if (now.isAfter(latest.getMarketTime().plusMinutes(15)) && now.isBefore(latest.getMarketTime().plusMinutes(23)) && !CollectionUtils.isEmpty(latest.getMargins())) {
            return processSignal(latest, orderAmount, brokerType);
        }
        return null;
    }

    private TargetStockResult processSignal(ChartInkBacktestMarginDto signal, BigDecimal orderAmount, BrokerType brokerType) {
        try {
            List<Margin> targetList;
            if (brokerType.equals(BrokerType.ZERODHA) || signal.getMargins().size() <= 1) {
                targetList = signal.getMargins();
            } else {
                targetList = signal.getMargins().stream().sorted(Comparator.comparing(Margin::getRupeezyMargin).reversed())
                        .toList();
            }

            for (var margin : targetList) {
                var target = processTargetMargin(margin, orderAmount, brokerType);
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

    private TargetStockResult processTargetMargin(Margin target, BigDecimal orderAmount, BrokerType brokerType) throws Exception {
        var ltp = angelOneService.getLTP(target.getToken());
        if (ltp == -2) {
            return null;
        }

        if (ltp == -1) {
            angelOneService.subscribe(target.getToken(), ExchangeType.NSE.getValue());
            ltp = awaitLtp(target.getToken());
            if (ltp < 0) {
                return null;
            }
        }

        BigDecimal requiredMargin = brokerType.equals(BrokerType.RUPEEZY) ? target.getRupeezyMargin() : target.getRequiredMargin();
        if (requiredMargin == null || requiredMargin.signum() <= 0) {
            return null;
        }

        int quantity = orderAmount.divide(BigDecimal.valueOf(ltp), 8, RoundingMode.HALF_UP)
                .multiply(requiredMargin)
                .setScale(0, RoundingMode.DOWN)
                .intValue();

        if (quantity > 0) {
            return new TargetStockResult(target, quantity);
        }

        return null;
    }

    private double awaitLtp(String token) throws InterruptedException {
        double ltp = angelOneService.getLTP(token);
        for (int attempt = 0; attempt < LTP_POLL_ATTEMPTS && ltp == -1; attempt++) {
            Thread.sleep(LTP_POLL_INTERVAL_MS);
            ltp = angelOneService.getLTP(token);
        }
        return ltp;
    }

    private boolean punchSingleTrade(Margin targetStock, int qty, long userId, StrategyOrder order) {
        log.info("Initiating trade for User: {} | Symbol: {} | Qty: {}", userId, targetStock.getSymbol(), qty);

        boolean entryPlaced = false;
        try {
            var orderRouter = orderRouterFactory.getRouter(order.getBroker());

            var req = TradeOrderRequest.builder().symbol(targetStock.getSymbol()).quantity(qty)
                    .transactionType(Constants.TRANSACTION_TYPE_BUY).orderType(Constants.ORDER_TYPE_MARKET).build();

            var orderResp = orderRouter.placeMTFOrder(order.getUserId(), req);
            entryPlaced = true;

            HelperUtil.pollWait(1000);

            var orderDetails = orderRouter.getOrderDetails(order.getUserId(), orderResp.getOrderId());
            double entryPrice = orderDetails.getAveragePrice().doubleValue();

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
                    com.app.shahbaztrades.util.Constants.NOTIFICATION_TITLE_BUY,
                    String.format(com.app.shahbaztrades.util.Constants.NOTIFICATION_MESSAGE_BUY, qty, targetStock.getSymbol(), entryPrice),
                    Collections.emptyMap()
            ));

            return true;

        } catch (Exception e) {
            log.error("Error in punchSingleTrade for {}", targetStock.getSymbol(), e);
            if (entryPlaced) {
                log.error("ORPHANED POSITION: entry placed for user {} symbol {} qty {} but exit/monitoring setup failed",
                        userId, targetStock.getSymbol(), qty);

                eventPublisher.publishEvent(new NotificationRequest(
                        userId,
                        com.app.shahbaztrades.util.Constants.NOTIFICATION_TITLE_BUY,
                        String.format(com.app.shahbaztrades.util.Constants.NOTIFICATION_MESSAGE_ORPHANED_POSITION,
                                qty, targetStock.getSymbol()),
                        Collections.emptyMap()
                ));
            }

            return entryPlaced;
        }
    }

    @EventListener
    @Async("taskExecutor")
    public void tradeCompletionListener(TradeCompletionEvent event) throws Exception {
        var orderRouter = orderRouterFactory.getRouter(event.trade().getBroker());
        var det = orderRouter.getOrderDetails(event.userId(), event.trade().getExitOrderId());
        if (det.getPendingQuantity() == 0) {
            log.info("Exit order filled for {}", event.trade().getSymbol());
            tradeWatchdog.unwatch(event.trade());
            activeOrders.remove(event.trade().getStrategyOrderId());
            eventPublisher.publishEvent(new NotificationRequest(
                    event.userId(),
                    com.app.shahbaztrades.util.Constants.NOTIFICATION_TITLE_SELL,
                    String.format(com.app.shahbaztrades.util.Constants.NOTIFICATION_MESSAGE_SELL, event.trade().getQuantity(),
                            event.trade().getSymbol(), event.trade().getTargetPrice()),
                    Collections.emptyMap()
            ));
        }
    }

}
