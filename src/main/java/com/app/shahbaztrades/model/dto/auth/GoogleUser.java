package com.app.shahbaztrades.model.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GoogleUser {
    String id;
    String email;
    @JsonProperty("verified_email")
    boolean verifiedEmail;
    String name;
    String picture;
    @JsonProperty("given_name")
    String givenName;
    @JsonProperty("family_name")
    String familyName;
}