package com.app.shahbaztrades.components.zerodha;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.OrderResponse;
import org.springframework.util.CollectionUtils;

import java.util.List;

public class ZerodhaOrderClient {

    public static OrderResponse placeMTFOrder(KiteConnect kc, String symbol, int qty, double price,
                                              String transactionType, String orderType) throws Exception, KiteException {

        OrderParams orderParams = new OrderParams();
        orderParams.exchange = Constants.EXCHANGE_NSE;
        orderParams.tradingsymbol = symbol;
        orderParams.transactionType = transactionType;
        orderParams.quantity = qty;
        orderParams.product = Constants.PRODUCT_MTF;
        orderParams.orderType = orderType;
        orderParams.validity = Constants.VALIDITY_DAY;
        orderParams.tag = "Shahbaz Trades";

        if (Constants.ORDER_TYPE_MARKET.equals(orderType)) {
            orderParams.marketProtection = -1.0;
            orderParams.price = null;
        } else {
            orderParams.price = price;
        }

        return kc.placeOrder(orderParams, Constants.VARIETY_REGULAR);
    }

    public static String placeMTFStopLossOrder(KiteConnect kc, String symbol, int qty, double price, double triggerPrice) throws Exception, KiteException {

        OrderParams orderParams = new OrderParams();

        orderParams.exchange = Constants.EXCHANGE_NSE;
        orderParams.tradingsymbol = symbol;
        orderParams.transactionType = Constants.TRANSACTION_TYPE_SELL;
        orderParams.quantity = qty;
        orderParams.price = price;
        orderParams.triggerPrice = triggerPrice;

        orderParams.product = Constants.PRODUCT_MTF;
        orderParams.orderType = Constants.ORDER_TYPE_SL;
        orderParams.validity = Constants.VALIDITY_DAY;

        OrderResponse orderResponse = kc.placeOrder(orderParams, Constants.VARIETY_REGULAR);

        if (orderResponse == null || orderResponse.orderId == null) {
            throw new Exception("Order placement failed: No Order ID returned");
        }

        return orderResponse.orderId;
    }

    public static Order getOrderDetails(KiteConnect kc, String orderId) throws Exception, KiteException {

        List<Order> history = kc.getOrderHistory(orderId);

        if (CollectionUtils.isEmpty(history)) {
            throw new Exception("No history found for order id " + orderId);
        }

        return history.getLast();
    }

    public static void updateMTFStopLossOrder(KiteConnect kc, String orderId, double newPrice, double newTriggerPrice) throws Exception, KiteException {
        OrderParams modParams = new OrderParams();
        modParams.price = newPrice;
        modParams.triggerPrice = newTriggerPrice;
        kc.modifyOrder(orderId, modParams, Constants.VARIETY_REGULAR);
    }

    public static Order cancelOrder(KiteConnect kc, String orderId) throws Exception {
        try {
            Order orderResponse = kc.cancelOrder(orderId, Constants.VARIETY_REGULAR, null);

            if (orderResponse == null) {
                throw new Exception("Failed to cancel order " + orderId + ": No response from server");
            }

            return orderResponse;
        } catch (Exception e) {
            throw new Exception(String.format("failed to cancel order %s: %s", orderId, e.getMessage()), e);
        } catch (KiteException e) {
            throw new RuntimeException(e);
        }
    }

    public static Order convertSLToMarket(KiteConnect kc, String orderId, int quantity) throws Exception, KiteException {
        OrderParams params = new OrderParams();
        params.orderType = Constants.ORDER_TYPE_MARKET;
        params.quantity = quantity;
        params.price = null;
        params.triggerPrice = null;
        params.marketProtection = -1.0;
        return kc.modifyOrder(orderId, params, Constants.VARIETY_REGULAR);
    }
}
