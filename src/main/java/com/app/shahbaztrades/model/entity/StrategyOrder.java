package com.app.shahbaztrades.model.entity;

import com.app.shahbaztrades.model.dto.order.StrategyOrderDto;
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
@Document(collection = "strategy_orders")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StrategyOrder {

    @MongoId
    String id;

    long userId;

    LocalDateTime date;

    String strategyName;

    float amount;

    public StrategyOrderDto toDto() {
        return StrategyOrderDto.builder()
                .id(id)
                .userId(userId)
                .strategyName(strategyName)
                .date(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .amount(amount)
                .build();
    }

}
