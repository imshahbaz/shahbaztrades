package com.app.shahbaztrades.model.dto.angelone.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SmartStreamParams(
        @JsonProperty("mode") int mode, // 1 for LTP, 2 for Quote, 3 for SnapQuote
        @JsonProperty("tokenList") List<TokenGroup> tokenList
) {
}