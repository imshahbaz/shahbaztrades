package com.app.shahbaztrades.model.entity;

import com.app.shahbaztrades.model.dto.order.OrderDto;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
@Document(collection = "zerodha_orders")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Order {

    @MongoId
    String id;

    long userId;

    String symbol;

    int quantity;

    LocalDateTime date;

    ExecutionRecord entry;

    ExecutionRecord exit;

    Margin margin;

    public OrderDto toDto() {
        return OrderDto.builder()
                .id(id)
                .userId(userId)
                .symbol(symbol)
                .quantity(quantity)
                .date(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ExecutionRecord {
        String brokerOrderId;
        String orderStatus;
        float averagePrice;
    }
}
