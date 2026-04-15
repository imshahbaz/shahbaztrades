package com.app.shahbaztrades.model.dto.zerodha;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ZerodhaLoginDto(
        @NotBlank
        @JsonProperty("request_token")
        String requestToken,

        @Min(1)
        @JsonProperty("user_id")
        long userId
) {
}