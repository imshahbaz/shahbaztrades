package com.app.shahbaztrades.model.dto.holdings;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.model.entity.Holdings;
import com.app.shahbaztrades.util.DateUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HoldingDto {

    @NotBlank
    String symbol;

    float margin;

    BigDecimal ltp;

    @Size(min = 1)
    List<@Valid HoldingDetailDto> holdingDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldNameConstants
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class HoldingDetailDto {
        int id;

        @Min(1)
        int quantity;

        @Min(1)
        BigDecimal price;

        @NotBlank
        String buyDate;

        public Holdings.HoldingDetail toHoldingDetail() {
            LocalDate parsedDate;
            try {
                parsedDate = LocalDate.parse(this.buyDate, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e) {
                throw new BadRequestException("Invalid date format");
            }

            return Holdings.HoldingDetail.builder()
                    .id(id)
                    .quantity(quantity)
                    .price(price)
                    .buyDate(parsedDate.atStartOfDay(DateUtil.IST_ZONE).toInstant())
                    .build();
        }
    }
}
