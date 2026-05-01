package com.app.shahbaztrades.model.dto.nse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NSEHistoricalData {

    @JsonProperty("chSymbol")
    String symbol;

    @JsonProperty("chOpeningPrice")
    double open;

    @JsonProperty("chTradeHighPrice")
    double high;

    @JsonProperty("chTradeLowPrice")
    double low;

    @JsonProperty("chClosingPrice")
    double close;

    @JsonProperty("mtimestamp")
    String timestamp;
}