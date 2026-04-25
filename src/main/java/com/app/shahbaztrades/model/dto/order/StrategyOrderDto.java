package com.app.shahbaztrades.model.dto.order;

import com.app.shahbaztrades.model.entity.StrategyOrder;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StrategyOrderDto {

    String id;

    long userId;

    @NotBlank
    String strategyName;

    @NotBlank
    String date;

    @Min(1)
    float amount;

    public StrategyOrder toEntity() {
        var date = LocalDate.parse(this.date, DateTimeFormatter.ISO_LOCAL_DATE);
        return StrategyOrder.builder()
                .id(id)
                .userId(userId)
                .strategyName(strategyName)
                .date(date.atStartOfDay())
                .amount(amount)
                .build();
    }

}
