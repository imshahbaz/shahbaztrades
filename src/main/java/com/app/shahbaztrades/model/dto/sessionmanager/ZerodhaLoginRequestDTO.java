package com.app.shahbaztrades.model.dto.sessionmanager;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record ZerodhaLoginRequestDTO(
        long userid,
        String username,
        String password,
        @JsonProperty("totp_secret") String totpSecret,
        @JsonProperty("api_key") String apiKey
) {
}