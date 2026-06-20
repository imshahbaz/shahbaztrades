package com.app.shahbaztrades.model.dto.rupeezy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RupeezyOrderResponseDto extends RupeezyBaseResponse {

    OrderData data;

    public String getOrderId() {
        if (this.data != null) {
            return this.data.getOrderId();
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
    }
}
