package com.app.shahbaztrades.model.dto.strategy;

import com.app.shahbaztrades.model.enums.BrokerType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ActiveTrade {
    String strategyOrderId;
    long userId;
    String strategyName;
    String symbol;
    String token;
    int quantity;
    double entryPrice;
    double targetPrice;
    double stopLoss;
    String entryOrderId;
    String exitOrderId;
    long timestamp;
    BrokerType broker;
}