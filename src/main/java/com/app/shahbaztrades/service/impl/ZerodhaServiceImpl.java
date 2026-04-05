package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.service.ZerodhaService;
import com.app.shahbaztrades.util.CacheUtils;
import com.app.shahbaztrades.util.DateUtil;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZerodhaServiceImpl implements ZerodhaService {

    private final UserService userService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public KiteConnect initiateKiteConnect(String accessToken, Long userId) throws Exception {
        log.info("Initiating KiteConnect for User ID: {}", userId);

        User user = userService.findByUserIdOrEmailOrMobile(userId, "", 0L);

        if (user == null) {
            throw new Exception("User not found with ID: " + userId);
        }

        if (user.getZerodhaConfig() == null ||
                StringUtils.isEmpty(user.getZerodhaConfig().getApiKey()) ||
                StringUtils.isEmpty(user.getZerodhaConfig().getApiSecret())) {
            throw new Exception("Zerodha API configuration is missing for this user");
        }

        KiteConnect kc = new KiteConnect(user.getZerodhaConfig().getApiKey());
        kc.setAccessToken(accessToken);
        return kc;
    }

    @Override
    public String generateAccessToken(String requestToken, Long userId) throws Exception, KiteException {
        log.info("Generating access token for User ID: {}", userId);

        User user = userService.findByUserIdOrEmailOrMobile(userId, "", 0L);

        if (user == null) {
            throw new Exception("User not found with ID: " + userId);
        }

        if (user.getZerodhaConfig() == null ||
                StringUtils.isEmpty(user.getZerodhaConfig().getApiKey()) ||
                StringUtils.isEmpty(user.getZerodhaConfig().getApiSecret())) {
            throw new Exception("Zerodha config not found");
        }

        KiteConnect kc = new KiteConnect(user.getZerodhaConfig().getApiKey());

        var userSession = kc.generateSession(requestToken, user.getZerodhaConfig().getApiSecret());

        if (userSession == null || StringUtils.isEmpty(userSession.accessToken)) {
            throw new Exception("Failed to generate session");
        }

        return userSession.accessToken;
    }

    @Override
    public KiteConnect getKiteClient(Long userId) throws Exception {

        var cachedClient = CacheUtils.kiteClientCache.getIfPresent(userId);
        if (cachedClient != null) {
            return cachedClient.client();
        }

        String accessToken = stringRedisTemplate.opsForValue().get("zerodha_token_" + userId);
        if (StringUtils.isEmpty(accessToken)) {
            throw new Exception("Access token not found in redis for user " + userId);
        }

        KiteConnect kc = initiateKiteConnect(accessToken, userId);

        CacheUtils.kiteClientCache.put(userId, new CacheUtils.CachedKiteClient(kc, DateUtil.zerodhaTokenExpiry()));

        return kc;
    }

    @Override
    public OrderResponse placeMTFOrder(KiteConnect kc, String symbol, int qty, double price,
                                       String transactionType, String orderType) throws Exception, KiteException {

        OrderParams orderParams = new OrderParams();
        orderParams.exchange = Constants.EXCHANGE_NSE;
        orderParams.tradingsymbol = symbol;
        orderParams.transactionType = transactionType;
        orderParams.quantity = qty;
        orderParams.price = price;
        orderParams.product = Constants.PRODUCT_MTF;
        orderParams.orderType = orderType;
        orderParams.validity = Constants.VALIDITY_DAY;
        orderParams.tag = "Shahbaz Trades";

        if (Constants.ORDER_TYPE_MARKET.equals(orderType)) {
            orderParams.marketProtection = -1.0;
        }

        return kc.placeOrder(orderParams, Constants.VARIETY_REGULAR);
    }

    @Override
    public String placeMTFStopLossOrder(KiteConnect kc, String symbol, int qty, double price, double triggerPrice) throws Exception, KiteException {

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

    @Override
    public Order getOrderDetails(KiteConnect kc, String orderId) throws Exception, KiteException {

        List<Order> history = kc.getOrderHistory(orderId);

        if (CollectionUtils.isEmpty(history)) {
            throw new Exception("No history found for order id " + orderId);
        }

        return history.getLast();
    }

    @Override
    public void updateMTFStopLossOrder(KiteConnect kc, String orderId, double newPrice, double newTriggerPrice) throws Exception, KiteException {
        OrderParams modParams = new OrderParams();
        modParams.price = newPrice;
        modParams.triggerPrice = newTriggerPrice;
        kc.modifyOrder(orderId, modParams, Constants.VARIETY_REGULAR);
    }

    @Override
    public Order cancelOrder(KiteConnect kc, String orderId) throws Exception {
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

    @Override
    public Order convertSLToMarket(KiteConnect kc, String orderId, int quantity, double price) throws Exception, KiteException {
        OrderParams params = new OrderParams();
        params.orderType = Constants.ORDER_TYPE_MARKET;
        params.quantity = quantity;
        params.price = price;
        params.marketProtection = -1.0;
        return kc.modifyOrder(orderId, params, Constants.VARIETY_REGULAR);
    }

}
