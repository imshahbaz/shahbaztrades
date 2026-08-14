package com.app.shahbaztrades.model.dto.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.io.Serial;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AIAnalysis implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty("action")
    String action;

    @JsonProperty("confidence")
    int confidence;

    @JsonProperty("reasoning")
    String reasoning;

    @JsonProperty("trend")
    String trend;

    @JsonProperty("tomorrow_high")
    float tomorrowHigh;

    @JsonProperty("tomorrow_low")
    float tomorrowLow;
}