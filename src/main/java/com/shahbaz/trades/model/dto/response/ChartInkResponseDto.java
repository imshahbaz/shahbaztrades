package com.shahbaz.trades.model.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChartInkResponseDto {

    List<StockData> data;

    @Data
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class StockData {
        String nsecode;
        String name;
        float close;
    }
}
