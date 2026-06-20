package com.app.shahbaztrades.model.dto.rupeezy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RupeezySessionResponse extends RupeezyBaseResponse {

    SessionData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class SessionData {
        @JsonProperty("access_token")
        String accessToken;
    }
}
