package com.app.shahbaztrades.model.dto.order;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.model.entity.StrategyOrder;
import com.app.shahbaztrades.util.DateUtil;
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
        LocalDate date;
        try {
            date = LocalDate.parse(this.date, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            throw new BadRequestException("Invalid date format");
        }

        return StrategyOrder.builder()
                .id(id)
                .userId(userId)
                .strategyName(strategyName)
                .date(date.atStartOfDay(DateUtil.IST_ZONE).toInstant())
                .amount(amount)
                .build();
    }

}
