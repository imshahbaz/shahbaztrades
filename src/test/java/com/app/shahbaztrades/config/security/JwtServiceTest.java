package com.app.shahbaztrades.config.security;

import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.JwtClaims;
import com.app.shahbaztrades.model.entity.MongoEnvConfig;
import com.app.shahbaztrades.model.enums.UserRole;
import com.app.shahbaztrades.service.MongoConfigService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private MongoConfigService mongoConfigService;

    private JwtService jwtService(String secret) {
        MongoEnvConfig config = new MongoEnvConfig();
        config.setJwtSecret(secret);
        lenient().when(mongoConfigService.getConfig()).thenReturn(config);
        return new JwtService(mongoConfigService, JsonMapper.builder().build());
    }

    private UserDto sampleUser() {
        return UserDto.builder().userId(42L).email("a@b.com").role(UserRole.USER).build();
    }

    @Test
    void generateThenValidate_roundTripsUser() {
        JwtService service = jwtService("this-is-a-sufficiently-long-secret-key!!");
        String token = service.generateToken(sampleUser());

        JwtClaims claims = service.validateToken(token);

        assertEquals(42L, claims.getUser().getUserId());
        assertEquals("42", claims.getSubject());
        assertEquals("shahbaz-trades", claims.getIssuer());
    }

    @Test
    void weakSecret_failsClosed() {
        JwtService service = jwtService("too-short");
        assertThrows(IllegalStateException.class, () -> service.generateToken(sampleUser()));
    }
}
