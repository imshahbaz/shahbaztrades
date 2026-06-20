package com.app.shahbaztrades.model.dto.order;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TradeOrderResponse {
    String orderId;
    String status;
    double averagePrice;
    int pendingQuantity;
}
