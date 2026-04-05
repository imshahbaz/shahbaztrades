package com.app.shahbaztrades.service;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderResponse;

public interface ZerodhaService {

    KiteConnect initiateKiteConnect(String accessToken, Long userId) throws Exception;

    String generateAccessToken(String requestToken, Long userId) throws Exception, KiteException;

    KiteConnect getKiteClient(Long userId) throws Exception;

    OrderResponse placeMTFOrder(KiteConnect kc, String symbol, int qty, double price,
                                String transactionType, String orderType) throws Exception, KiteException;

    String placeMTFStopLossOrder(KiteConnect kc, String symbol, int qty, double price, double triggerPrice) throws Exception, KiteException;

    Order getOrderDetails(KiteConnect kc, String orderId) throws Exception, KiteException;

    void updateMTFStopLossOrder(KiteConnect kc, String orderId, double newPrice, double newTriggerPrice) throws Exception, KiteException;

    Order cancelOrder(KiteConnect kc, String orderId) throws Exception;

    Order convertSLToMarket(KiteConnect kc, String orderId, int quantity, double price) throws Exception, KiteException;
}
