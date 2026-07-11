package com.app.shahbaztrades.model.dto.rupeezy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RupeezyOrderHistory extends RupeezyBaseResponse {

    List<OrderData> orders;

    public OrderData getOrder(String orderId) {
        if (CollectionUtils.isEmpty(orders)) {
            return null;
        }
        for (OrderData order : orders) {
            if (order.getOrderId().equals(orderId)) {
                return order;
            }
        }
        return null;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class OrderData {
        @JsonProperty("order_id")
        String orderId;

        String status;

        String ticker;

        String symbol;

        @JsonProperty("total_quantity")
        int totalQuantity;

        @JsonProperty("pending_quantity")
        int pendingQuantity;

        @JsonProperty("traded_price")
        double averagePrice;
    }

}
