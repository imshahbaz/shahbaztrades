package com.app.shahbaztrades.components.trading;

import com.app.shahbaztrades.components.observer.TradeWatchdog;
import com.app.shahbaztrades.components.orderrouting.OrderRouterFactory;
import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.dto.strategy.ActiveTrade;
import com.app.shahbaztrades.model.dto.strategy.TradeCompletionEvent;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.entity.StrategyOrder;
import com.app.shahbaztrades.util.HelperUtil;
import com.zerodhatech.kiteconnect.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Places and settles a single continuous trade: market entry, target exit, and handing the position
 * to the watchdog.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContinuousTradeExecutor {

    /** Gives the broker time to report a fill price before the exit is sized against it. */
    private static final long ENTRY_FILL_WAIT_MS = 1000;

    private final OrderRouterFactory orderRouterFactory;
    private final TradeWatchdog tradeWatchdog;
    private final TradeNotifier tradeNotifier;

    /**
     * Buys at market, then places the limit exit and starts watching the position.
     *
     * @return true if the entry order reached the broker. A true return with a logged orphan means
     * the position is open but unprotected, so the caller must not release its in-flight guard.
     */
    public boolean openTrade(StrategyOrder order, Margin targetStock, int qty) {
        long userId = order.getUserId();
        log.info("Initiating trade for User: {} | Symbol: {} | Qty: {}", userId, targetStock.getSymbol(), qty);

        boolean entryPlaced = false;
        try {
            var orderRouter = orderRouterFactory.getRouter(order.getBroker());

            var entryReq = TradeOrderRequest.builder().symbol(targetStock.getSymbol()).quantity(qty)
                    .transactionType(Constants.TRANSACTION_TYPE_BUY).orderType(Constants.ORDER_TYPE_MARKET).build();

            var orderResp = orderRouter.placeMTFOrder(userId, entryReq);
            entryPlaced = true;

            HelperUtil.pollWait(ENTRY_FILL_WAIT_MS);

            var orderDetails = orderRouter.getOrderDetails(userId, orderResp.getOrderId());
            double entryPrice = orderDetails.getAveragePrice().doubleValue();
            double targetPrice = HelperUtil.dynamicTargetPrice(order.getAmount(), orderDetails.getAveragePrice(), qty);

            log.info("Entry Executed at: {} | Target Set at: {}", entryPrice, targetPrice);

            var exitReq = TradeOrderRequest.builder().symbol(targetStock.getSymbol()).quantity(qty).price(targetPrice)
                    .transactionType(Constants.TRANSACTION_TYPE_SELL).orderType(Constants.ORDER_TYPE_LIMIT).build();

            var exitResp = orderRouter.placeMTFOrder(userId, exitReq);

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

            tradeNotifier.buyExecuted(userId, qty, targetStock.getSymbol(), entryPrice);
            return true;

        } catch (Exception e) {
            log.error("Error in punchSingleTrade for {}", targetStock.getSymbol(), e);
            if (entryPlaced) {
                log.error("ORPHANED POSITION: entry placed for user {} symbol {} qty {} but exit/monitoring setup failed",
                        userId, targetStock.getSymbol(), qty);
                tradeNotifier.orphanedPosition(userId, qty, targetStock.getSymbol());
            }

            return entryPlaced;
        }
    }

    /**
     * Checks whether the target exit actually filled, and if so stops watching the position.
     *
     * @return true once the exit is fully filled, so the caller can release the order.
     */
    public boolean closeIfFilled(TradeCompletionEvent event) {
        var trade = event.trade();
        var orderRouter = orderRouterFactory.getRouter(trade.getBroker());
        var details = orderRouter.getOrderDetails(event.userId(), trade.getExitOrderId());
        if (details.getPendingQuantity() != 0) {
            return false;
        }

        log.info("Exit order filled for {}", trade.getSymbol());
        tradeWatchdog.unwatch(trade);
        tradeNotifier.sellExecuted(event.userId(), trade.getQuantity(), trade.getSymbol(), trade.getTargetPrice());
        return true;
    }
}
