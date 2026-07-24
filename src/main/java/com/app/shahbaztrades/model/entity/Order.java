package com.app.shahbaztrades.model.entity;

import com.app.shahbaztrades.model.dto.analysis.TechnicalMetrics;
import com.app.shahbaztrades.model.dto.order.OrderDto;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.model.enums.OrderStatus;
import com.app.shahbaztrades.util.DateUtil;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.math.BigDecimal;
import java.time.Instant;
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

    Instant date;

    ExecutionRecord entry;

    ExecutionRecord exit;

    Margin margin;

    BrokerType broker;

    OrderStatus orderStatus = OrderStatus.PENDING;

    TechnicalMetrics atr;

    public OrderDto toDto() {
        return OrderDto.builder()
                .id(this.id)
                .userId(this.userId)
                .symbol(this.symbol)
                .quantity(this.quantity)
                .date(DateTimeFormatter.ISO_LOCAL_DATE.withZone(DateUtil.IST_ZONE).format(this.date))
                .broker(this.broker)
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
        BigDecimal averagePrice;
    }
}
