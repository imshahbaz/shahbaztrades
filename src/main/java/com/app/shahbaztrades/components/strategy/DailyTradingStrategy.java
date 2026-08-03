package com.app.shahbaztrades.components.strategy;

import com.app.shahbaztrades.model.dto.analysis.TechnicalMetrics;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.model.enums.OrderStatus;
import com.zerodhatech.kiteconnect.utils.Constants;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

public interface DailyTradingStrategy {

    static OrderStatus mapEntryStatus(String brokerStatus) {
        if (StringUtils.isBlank(brokerStatus)) {
            return OrderStatus.PLACED;
        }

        return switch (brokerStatus.toUpperCase()) {
            case Constants.ORDER_COMPLETE, "EXECUTED" -> OrderStatus.BOUGHT;
            case Constants.ORDER_REJECTED -> OrderStatus.REJECTED;
            case Constants.ORDER_CANCELLED, "CANCELLED AMO" -> OrderStatus.FAILED;
            default -> OrderStatus.PLACED;
        };
    }

    String getName();

    void initialiseTrade(Order order, Map<String, TechnicalMetrics> metrics);

    void updateTradeStatus(Order order);

    void startTrading(Order order);
}
