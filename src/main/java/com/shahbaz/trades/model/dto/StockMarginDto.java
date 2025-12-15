package com.shahbaz.trades.model.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StockMarginDto {
    String name;
    String symbol;
    float margin;
    float close;
}
