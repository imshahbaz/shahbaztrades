package com.app.shahbaztrades.model.dto.order;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.entity.Order;
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
public class OrderDto {

    String id;

    @Min(1)
    long userId;

    @NotBlank
    String symbol;

    @Min(1)
    int quantity;

    @NotBlank
    String date;

    public Order toEntity(Margin margin) {
        LocalDate date;
        try {
            date = LocalDate.parse(this.date, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            throw new BadRequestException("Invalid date format");
        }

        return Order.builder()
                .id(this.id)
                .userId(this.userId)
                .symbol(this.symbol)
                .quantity(this.quantity)
                .date(date.atStartOfDay(DateUtil.IST_ZONE).toInstant())
                .margin(margin).build();
    }
}
