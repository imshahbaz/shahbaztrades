package com.app.shahbaztrades.model.dto.angelone.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SmartStreamRequest(
    @JsonProperty("correlationId") String correlationId,
    @JsonProperty("action") int action, // 1 for Subscribe, 2 for Unsubscribe
    @JsonProperty("params") SmartStreamParams params
) {}