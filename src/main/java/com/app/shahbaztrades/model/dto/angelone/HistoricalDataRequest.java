package com.app.shahbaztrades.model.dto.angelone;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HistoricalDataRequest {

    @JsonProperty("exchange")
    String exchange;

    @JsonProperty("symboltoken")
    String symbolToken;

    @JsonProperty("interval")
    String interval;

    @JsonProperty("fromdate")
    String fromDate;

    @JsonProperty("todate")
    String toDate;
}