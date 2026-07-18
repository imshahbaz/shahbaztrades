package com.app.shahbaztrades.config.security;

import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.JwtClaims;
import com.app.shahbaztrades.service.MongoConfigService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;
    private final MongoConfigService mongoConfigService;
    private final JsonMapper jsonMapper;
    private volatile String cachedSecret;
    private volatile SecretKey cachedKey;

    private SecretKey key() {
        String secret = mongoConfigService.getConfig().getJwtSecret();
        SecretKey local = cachedKey;
        if (!secret.equals(cachedSecret)) {
            byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (secretBytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "Configured JWT secret is too short; require at least " + MIN_SECRET_BYTES + " bytes for HS256");
            }

            local = Keys.hmacShaKeyFor(secretBytes);
            cachedKey = local;
            cachedSecret = secret;
        }
        return local;
    }

    public JwtClaims validateToken(String tokenString) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(tokenString)
                .getPayload();

        if (!"shahbaz-trades".equals(claims.getIssuer())) {
            throw new UnauthorizedException("Invalid session");
        }

        UserDto user = jsonMapper.convertValue(claims.get("user"), UserDto.class);
        if (user == null) {
            throw new UnauthorizedException("Invalid session");
        }

        String expectedSub = String.valueOf(user.getUserId());
        if (!expectedSub.equals(claims.getSubject())) {
            throw new UnauthorizedException("Invalid session");
        }

        return JwtClaims.builder()
                .user(user)
                .issuer(claims.getIssuer())
                .subject(claims.getSubject())
                .issuedAt(claims.getIssuedAt().getTime())
                .expiresAt(claims.getExpiration().getTime())
                .build();
    }

    public String generateToken(UserDto user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + 86400000);

        return Jwts.builder()
                .issuer("shahbaz-trades")
                .subject(String.valueOf(user.getUserId()))
                .issuedAt(now)
                .expiration(expiration)
                .claim("user", user)
                .signWith(key())
                .compact();
    }

}