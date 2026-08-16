package com.app.shahbaztrades.model.dto.order;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.model.entity.StrategyOrder;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.model.enums.OrderStatus;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.validator.OrderValidator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
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
    BigDecimal amount;

    @NotNull
    BrokerType broker;

    OrderStatus orderStatus;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getStatusLabel() {
        return this.orderStatus == null ? null : this.orderStatus.getLabel();
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getStatusColor() {
        return this.orderStatus == null ? null : this.orderStatus.getColor();
    }

    public StrategyOrder toEntity() {
        LocalDate parsedDate;
        try {
            parsedDate = LocalDate.parse(this.date, DateTimeFormatter.ISO_LOCAL_DATE);
            OrderValidator.validateOrderDate(parsedDate);
        } catch (Exception _) {
            throw new BadRequestException("Invalid date format");
        }

        return StrategyOrder.builder()
                .id(this.id)
                .userId(this.userId)
                .strategyName(this.strategyName)
                .date(parsedDate.atStartOfDay(DateUtil.IST_ZONE).toInstant())
                .amount(this.amount)
                .broker(this.broker)
                .build();
    }

}
