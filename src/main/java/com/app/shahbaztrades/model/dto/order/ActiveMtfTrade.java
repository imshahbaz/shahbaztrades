package com.app.shahbaztrades.model.dto.order;

import com.app.shahbaztrades.model.entity.Order;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ActiveMtfTrade {
    double ltp;
    @Builder.Default
    double prevLtp = 0;
    float peakPrice;
    Order order;
}
