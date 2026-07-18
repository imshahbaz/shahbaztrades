package com.app.shahbaztrades.model.dto.chartink;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StockMarginDto {

    String name;

    String symbol;

    BigDecimal margin;

    BigDecimal rupeezyMargin;

    float close;
}