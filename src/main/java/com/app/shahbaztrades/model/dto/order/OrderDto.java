package com.app.shahbaztrades.model.dto.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

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
}
