package com.app.shahbaztrades.model.dto.angelone;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MinimalInstrument(
        @JsonProperty("name") String name,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("token") String token,
        @JsonProperty("exch_seg") String exchSeg,
        @JsonProperty("expiry") String expiry,
        @JsonProperty("lotsize") String lotSize,
        @JsonProperty("strike") String strike
) {
}