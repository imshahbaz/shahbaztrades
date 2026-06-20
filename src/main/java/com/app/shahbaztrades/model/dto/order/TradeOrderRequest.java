package com.app.shahbaztrades.model.dto.order;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TradeOrderRequest {
    private String symbol;
    private int quantity;
    private Double price;
    private Double triggerPrice;
    private String transactionType;
    private String orderType;
    private String orderId;
}