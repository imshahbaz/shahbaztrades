package com.app.shahbaztrades.model.dto.rupeezy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RupeezyOrderHistory extends RupeezyBaseResponse {

    List<OrderData> data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class OrderData {
        @JsonProperty("order_id")
        String orderId;

        String ticker;

        String status;

        @JsonProperty("total_quantity")
        int totalQuantity;

        @JsonProperty("pending_quantity")
        int pendingQuantity;
    }
}
