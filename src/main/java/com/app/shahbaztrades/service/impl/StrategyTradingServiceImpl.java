package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.helper.AsyncHelper;
import com.app.shahbaztrades.model.dto.chartink.ChartInkBacktestMarginDto;
import com.app.shahbaztrades.model.dto.fcm.NotificationRequest;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import com.app.shahbaztrades.model.dto.strategy.TargetStockResult;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.entity.StrategyOrder;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.service.*;
import com.app.shahbaztrades.util.CacheUtils;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyTradingServiceImpl implements StrategyTradingService {

    private static final Set<String> targets = Set.of(
            "09:35", "09:50", "10:05", "10:20", "10:35", "10:50",
            "11:05", "11:20", "11:35", "11:50", "12:05", "12:20",
            "12:35", "12:50", "13:05", "13:20", "13:35", "13:50",
            "14:05", "14:20", "14:35", "14:50", "15:05"
    );

    private static final DateTimeFormatter HOUR_MIN_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final Map<String, ScheduledFuture<?>> runningPollers = new ConcurrentHashMap<>();
    private final StrategyOrderService strategyOrderService;
    private final StrategyService strategyService;
    private final ChartInkService chartInkService;
    private final ZerodhaService zerodhaService;
    private final AngelOneWebSocketService angelOneWebSocketService;
    private final AsyncHelper asyncHelper;

    @Override
    public void continuousTrade() {
        var orders = strategyOrderService.getTodayOrders();
        if (CollectionUtils.isEmpty(orders)) {
            log.info("No orders found for continuous trading");
            return;
        }

        Set<String> processedStrategies = new HashSet<>();

        for (var order : orders) {
            String strategyName = order.getStrategyName();
            if (processedStrategies.contains(strategyName)) {
                continue;
            }
            processedStrategies.add(strategyName);
            var strategy = strategyService.getCachedStrategies().get(strategyName);

            if (strategy == null) {
                log.error("Strategy configuration not found for {}", strategyName);
                continue;
            }

            HelperUtil.EXECUTOR.execute(() -> startManualPoller(strategy));
        }

        log.info("Starting strategy trading order count {}", orders.size());

        for (var order : orders) {
            KiteConnect kc;
            try {
                kc = zerodhaService.getKiteClient(order.getUserId());
            } catch (Exception e) {
                log.error("Error connecting to Kite Client for user {} error {}", order.getUserId(), e.getMessage());
                continue;
            }

            HelperUtil.EXECUTOR.execute(() -> tradeLoop(order, kc));
        }

    }

    public void startManualPoller(StrategyDto strategy) {
        runningPollers.computeIfAbsent(strategy.getName(), name -> {
            log.info("Manual Watchdog Poller started for strategy {}", name);
            return HelperUtil.SCHEDULER.scheduleAtFixedRate(() -> HelperUtil.EXECUTOR.execute(() -> {
                if (DateUtil.isSquareOffTimeReached()) {
                    log.info("Market closed. Watchdog exiting.");
                    var task = runningPollers.remove(name);
                    if (task != null) {
                        task.cancel(false);
                    }
                    return;
                }

                String currentTime = LocalTime.now(DateUtil.IST_ZONE).format(HOUR_MIN_FORMATTER);

                if (targets.contains(currentTime)) {
                    log.info("Target match at time {} ! Fetching signals...", currentTime);
                    try {
                        var signals = chartInkService.fetchTodayBacktestDataWithMargin(name);
                        CacheUtils.pollerCache.set(name, signals, Duration.ofMinutes(3));
                        log.info("Complete signals list: {}", signals);
                    } catch (Exception e) {
                        log.error("Manual fetch failed", e);
                    }
                }
            }), 1, 1, TimeUnit.MINUTES);
        });
    }

    private void tradeLoop(StrategyOrder order, KiteConnect kc) {
        log.info("Started trading loop for user {} using {}", order.getUserId(), order.getStrategyName());
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (DateUtil.isMarketClosedForTrading()) {
                    log.info("Market closing. Stopping trade loop for userId: {}", order.getUserId());
                    break;
                }

                var signals = CacheUtils.pollerCache.get(order.getStrategyName());
                if (CollectionUtils.isEmpty(signals)) {
                    Thread.sleep(Duration.ofMinutes(1));
                    continue;
                }

                var target = findTargetStock(signals, order.getAmount());

                if (target == null) {
                    Thread.sleep(Duration.ofMinutes(1));
                    continue;
                }

                boolean success = punchSingleTrade(kc, target.margin(), target.qty(), order.getUserId());

                if (!success) {
                    log.warn("Punch trade failed for userId: {} symbol: {}. Ending loop.",
                            order.getUserId(), target.getSymbol());
                    break;
                }

                log.info("Target Achieved! userId {} symbol: {}. Looking for next opportunity.", order.getUserId(), target.getSymbol());
            }
        } catch (InterruptedException e) {
            log.info("Trade loop for user {} stopped via interruption", order.getUserId());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Unexpected error in trade loop for user {}", order.getUserId(), e);
        }
    }

    private TargetStockResult findTargetStock(List<ChartInkBacktestMarginDto> signals, float orderAmount) {
        for (int i = signals.size() - 1; i >= 0; i--) {
            var signal = signals.get(i);
            try {
                var now = DateUtil.getCurrentDateTime();
                if (now.isAfter(signal.getMarketTime().plusMinutes(20)) && now.isBefore(signal.getMarketTime().plusMinutes(23))) {
                    if (!CollectionUtils.isEmpty(signal.getMargins())) {
                        var target = signal.getMargins().getFirst();
                        angelOneWebSocketService.subscribe(target.getToken(), ExchangeType.NSE.getValue());
                        Thread.sleep(1000);
                        var ltp = angelOneWebSocketService.getLTP(target.getToken());
                        if (ltp <= 0) {
                            continue;
                        }
                        int quantity = (int) (orderAmount / target.getMargin());
                        if (quantity > 0) {
                            return new TargetStockResult(target, quantity);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error processing signal {}", e.getMessage());
            }
        }

        return null;
    }

    private boolean punchSingleTrade(KiteConnect kc, Margin targetStock, int qty, long userId) {
        log.info("Placing entry order for symbol: {} qty: {}", targetStock.getSymbol(), qty);

        try {
            var orderResp = zerodhaService.placeMTFOrder(kc, targetStock.getSymbol(), qty, 0,
                    Constants.TRANSACTION_TYPE_BUY, Constants.ORDER_TYPE_MARKET);

            Thread.sleep(1000);
            var od = zerodhaService.getOrderDetails(kc, orderResp.orderId);

            double entryPrice = Double.parseDouble(od.averagePrice);
            double targetPrice = HelperUtil.fixToTick(entryPrice * 1.004);

            log.info("Entry: {} | Target: {} | Symbol: {}", entryPrice, targetPrice, targetStock.getSymbol());

            var sOd = zerodhaService.placeMTFOrder(
                    kc, targetStock.getSymbol(), qty, targetPrice, Constants.TRANSACTION_TYPE_SELL, Constants.ORDER_TYPE_LIMIT
            );

            asyncHelper.post(new NotificationRequest(
                    userId,
                    "Order Placed",
                    String.format("%s | Entry: %.2f | Target: %.2f", targetStock.getSymbol(), entryPrice, targetPrice),
                    Collections.emptyMap()
            ));

            double prevLtp = 0;
            while (!Thread.currentThread().isInterrupted()) {

                if (DateUtil.isSquareOffTimeReached()) {
                    log.info("Square-off reached. Exiting monitor for {}", targetStock.getSymbol());
                    return false;
                }

                double ltp = angelOneWebSocketService.getLTP(targetStock.getToken());

                if (ltp == -2) {
                    log.error("Lost LTP feed for {}", targetStock.getSymbol());
                    return false;
                }

                if (ltp != prevLtp) {
                    if (ltp >= targetPrice) {
                        var det = zerodhaService.getOrderDetails(kc, sOd.orderId);
                        if (Integer.parseInt(det.pendingQuantity) == 0) {
                            log.info("Exit order filled for {}", targetStock.getSymbol());
                            asyncHelper.post(new NotificationRequest(
                                    userId,
                                    "Target Achieved!",
                                    String.format("%s | Target: %.2f", targetStock.getSymbol(), targetPrice),
                                    Collections.emptyMap()
                            ));
                            return true;
                        }
                    }
                    prevLtp = ltp;
                }

                if (!HelperUtil.pollWait(200)) {
                    return false;
                }
            }

        } catch (InterruptedException e) {
            log.warn("Trade monitor interrupted for {}", targetStock.getSymbol());
            Thread.currentThread().interrupt();
        } catch (KiteException | Exception e) {
            log.error("Error in punchSingleTrade for {} error {}", targetStock.getSymbol(), e.getMessage());
        }

        return false;
    }

}
