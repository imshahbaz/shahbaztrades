package com.app.shahbaztrades.model.entity;

import com.app.shahbaztrades.model.dto.order.StrategyOrderDto;
import com.app.shahbaztrades.model.enums.BrokerType;
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
@Document(collection = "strategy_orders")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StrategyOrder {

    @MongoId
    String id;

    long userId;

    Instant date;

    String strategyName;

    BigDecimal amount;

    BrokerType broker;

    public StrategyOrderDto toDto() {
        return StrategyOrderDto.builder()
                .id(this.id)
                .userId(this.userId)
                .strategyName(this.strategyName)
                .date(DateTimeFormatter.ISO_LOCAL_DATE.withZone(DateUtil.IST_ZONE).format(this.date))
                .amount(this.amount)
                .broker(this.broker)
                .build();
    }

}
