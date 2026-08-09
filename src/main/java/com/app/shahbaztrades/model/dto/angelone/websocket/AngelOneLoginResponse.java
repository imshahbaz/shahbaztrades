package com.app.shahbaztrades.model.dto.angelone.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.io.Serial;
import java.io.Serializable;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AngelOneLoginResponse {
    boolean status;
    String message;
    @JsonProperty("errorcode")
    String errorCode;
    LoginData data;

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class LoginData implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        String jwtToken;

        String refreshToken;

        String feedToken;
    }
}