package com.app.shahbaztrades.model.dto.chartink;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StockMarginDto {

    String name;

    String symbol;

    float margin;

    float close;

    float deliveryPercent;
}