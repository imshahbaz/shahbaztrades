package com.app.shahbaztrades.model.dto.sessionmanager;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record ZerodhaLoginResponseDTO(
        String status,
        String message,
        String error,
        long userid,
        @JsonProperty("request_token") String requestToken
) {
    public boolean isPending() {
        return "PENDING".equalsIgnoreCase(status);
    }

    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(status);
    }

    public boolean isError() {
        return "ERROR".equalsIgnoreCase(status);
    }
}