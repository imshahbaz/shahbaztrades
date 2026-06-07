package com.app.shahbaztrades.model.dto.sessionmanager;

import com.app.shahbaztrades.model.entity.User;
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
    public static ZerodhaLoginRequestDTO mapDto(long userId, User.ZerodhaConfig config) {
        return ZerodhaLoginRequestDTO.builder()
                .userid(userId)
                .username(config.getUserName())
                .password(config.getPassword())
                .totpSecret(config.getTotpSecret())
                .apiKey(config.getApiKey())
                .build();
    }
}