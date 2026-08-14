package com.app.shahbaztrades.model.dto.nse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NSEHistoricalData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

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