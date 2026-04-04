package com.app.shahbaztrades.model.dto.auth;

import com.app.shahbaztrades.model.dto.UserDto;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JwtClaims {
    UserDto user;
    String issuer;
    String subject;
    long issuedAt;
    long expiresAt;
}