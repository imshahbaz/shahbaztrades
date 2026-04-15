package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.zerodha.ZerodhaLoginDto;
import com.app.shahbaztrades.model.entity.User;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderResponse;
import org.springframework.http.ResponseEntity;

public interface ZerodhaService {

    String ZERODHA_TOKEN_KEY = "zerodha_token_";

    KiteConnect initiateKiteConnect(String accessToken, Long userId);

    String generateAccessToken(String requestToken, Long userId);

    KiteConnect getKiteClient(Long userId);

    OrderResponse placeMTFOrder(KiteConnect kc, String symbol, int qty, double price,
                                String transactionType, String orderType) throws Exception, KiteException;

    String placeMTFStopLossOrder(KiteConnect kc, String symbol, int qty, double price, double triggerPrice) throws Exception, KiteException;

    Order getOrderDetails(KiteConnect kc, String orderId) throws Exception, KiteException;

    void updateMTFStopLossOrder(KiteConnect kc, String orderId, double newPrice, double newTriggerPrice) throws Exception, KiteException;

    Order cancelOrder(KiteConnect kc, String orderId) throws Exception;

    Order convertSLToMarket(KiteConnect kc, String orderId, int quantity, double price) throws Exception, KiteException;

    ResponseEntity<ApiResponse<Void>> login(ZerodhaLoginDto request);

    ResponseEntity<ApiResponse<String>> getAuth(UserDto userDto);

    ResponseEntity<ApiResponse<Long>> setConfig(User.ZerodhaConfig config, UserDto userDto);
}
