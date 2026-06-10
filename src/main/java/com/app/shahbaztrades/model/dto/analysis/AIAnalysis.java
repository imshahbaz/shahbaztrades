package com.app.shahbaztrades.model.dto.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

public record AIAnalysis(
        @JsonProperty("action") String action,
        @JsonProperty("confidence") int confidence,
        @JsonProperty("reasoning") String reasoning,
        @JsonProperty("trend") String trend,
        @JsonProperty("tomorrow_high") @SerializedName("tomorrow_high") float tomorrowHigh,
        @JsonProperty("tomorrow_low") @SerializedName("tomorrow_low") float tomorrowLow
) {
}