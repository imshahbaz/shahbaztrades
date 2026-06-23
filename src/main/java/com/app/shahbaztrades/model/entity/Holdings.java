package com.app.shahbaztrades.model.entity;

import com.app.shahbaztrades.model.dto.holdings.HoldingDto;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.util.DateUtil;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
@Document(collection = "holdings")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Holdings {

    @MongoId
    long userId;

    @Builder.Default
    Map<BrokerType, List<HoldingInfo>> brokerHoldingMap = new ConcurrentHashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldNameConstants
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class HoldingInfo {
        String symbol;

        float margin;

        @Builder.Default
        List<HoldingDetail> holdingDetails = new CopyOnWriteArrayList<>();

        public HoldingDto toHoldingDto() {
            return HoldingDto.builder()
                    .symbol(this.symbol)
                    .margin(this.margin)
                    .holdingDetails(this.holdingDetails.stream()
                            .map(HoldingDetail::toHoldingDetailDto).toList())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldNameConstants
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class HoldingDetail {
        int id;
        int quantity;
        BigDecimal price;
        Instant buyDate;

        public HoldingDto.HoldingDetailDto toHoldingDetailDto() {
            return HoldingDto.HoldingDetailDto.builder()
                    .id(this.id)
                    .quantity(this.quantity)
                    .price(this.price)
                    .buyDate(DateTimeFormatter.ISO_LOCAL_DATE.withZone(DateUtil.IST_ZONE).format(this.buyDate))
                    .build();
        }
    }
}
