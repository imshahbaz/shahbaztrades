package com.app.shahbaztrades.model.dto.angelone.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TokenGroup(
        @JsonProperty("exchangeType") int exchangeType,
        @JsonProperty("tokens") List<String> tokens
) {
}