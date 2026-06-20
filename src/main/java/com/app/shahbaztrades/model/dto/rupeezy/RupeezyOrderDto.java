package com.app.shahbaztrades.model.dto.rupeezy;

import com.app.shahbaztrades.util.DateUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RupeezyOrderDto {

    private String ticker;

    @JsonProperty("transaction_type")
    private String transactionType;

    private String product;

    private String variety;

    private int quantity;

    private float price;

    @JsonProperty("trigger_price")
    private Float triggerPrice;

    @JsonProperty("disclosed_quantity")
    private int disclosedQuantity;

    @JsonProperty("traded_quantity")
    private int tradedQuantity;

    @Builder.Default
    private String validity = "DAY";

    @Builder.Default
    @JsonProperty("is_amo")
    private boolean isAmo = DateUtil.isMarketClosedForTrading();
}