package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.auth.SessionCookieIssuer;
import com.app.shahbaztrades.config.security.JwtService;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.AuthRequest;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.model.enums.UserRole;
import com.app.shahbaztrades.repo.redis.AuthDataRedisRepo;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.util.AuthUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String ENCRYPTION_KEY = "state-signing-key";

    @Mock
    private UserService userService;
    @Mock
    private JwtService jwtService;
    @Mock
    private SessionCookieIssuer sessionCookieIssuer;
    @Mock
    private AuthDataRedisRepo<UserDto> authDataRedisRepo;
    @Mock
    private HttpServletResponse servletResponse;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userService, jwtService, sessionCookieIssuer, authDataRedisRepo);
    }

    private User user() {
        return User.builder().userId(7L).email("jane@example.com").role(UserRole.USER)
                .password(AuthUtil.ENCODER.encode("pw")).build();
    }

    // --- logout / me ------------------------------------------------------

    @Test
    void logout_returnsAnExpiringAuthCookie() {
        when(sessionCookieIssuer.expire()).thenReturn("auth_token=; Max-Age=0");

        assertEquals("auth_token=; Max-Age=0", service.logout());
    }

    @Test
    void getMe_servesTheRedisCacheWithoutHittingMongo() {
        UserDto cached = UserDto.builder().userId(7L).email("jane@example.com").build();
        when(authDataRedisRepo.get("7")).thenReturn(cached);

        assertSame(cached, service.getMe(UserDto.builder().userId(7L).build()));

        verify(userService, never()).findByUserIdOrEmailOrMobile(anyLong(), anyString(), anyLong());
    }

    @Test
    void getMe_loadsAndCachesOnAMiss() {
        when(authDataRedisRepo.get("7")).thenReturn(null);
        when(userService.findByUserIdOrEmailOrMobile(eq(7L), any(), any())).thenReturn(user());

        UserDto dto = service.getMe(UserDto.builder().userId(7L).build());

        assertEquals("jane@example.com", dto.getEmail());
        verify(authDataRedisRepo).set(eq("7"), any(UserDto.class), eq(Duration.ofHours(1)));
    }

    @Test
    void getMe_rejectsATokenForAUserThatNoLongerExists() {
        when(authDataRedisRepo.get("7")).thenReturn(null);
        when(userService.findByUserIdOrEmailOrMobile(eq(7L), any(), any())).thenReturn(null);

        assertThrows(UnauthorizedException.class,
                () -> service.getMe(UserDto.builder().userId(7L).build()));
    }

    // --- password login ---------------------------------------------------

    @Test
    void login_setsTheAuthCookieOnASuccessfulPasswordMatch() {
        when(userService.findByUserIdOrEmailOrMobile(eq(0L), eq("jane@example.com"), eq(0L))).thenReturn(user());
        when(jwtService.generateToken(any(UserDto.class))).thenReturn("jwt-token");
        when(sessionCookieIssuer.issue("jwt-token")).thenReturn("auth_token=jwt-token");

        var response = service.login(new AuthRequest("jane@example.com", "pw", "pw"), servletResponse);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(7L, response.getBody().getData().getUserId());
        verify(servletResponse).addHeader(eq(HttpHeaders.SET_COOKIE), contains("auth_token=jwt-token"));
    }

    @Test
    void login_rejectsAWrongPasswordWithoutIssuingAToken() {
        when(userService.findByUserIdOrEmailOrMobile(eq(0L), anyString(), eq(0L))).thenReturn(user());

        assertThrows(BadRequestException.class,
                () -> service.login(new AuthRequest("jane@example.com", "wrong", "wrong"), servletResponse));
        verify(jwtService, never()).generateToken(any(UserDto.class));
        verify(servletResponse, never()).addHeader(anyString(), anyString());
    }

    @Test
    void login_reportsAnUnknownEmailAsNotFound() {
        when(userService.findByUserIdOrEmailOrMobile(eq(0L), anyString(), eq(0L))).thenReturn(null);

        assertThrows(NotFoundException.class,
                () -> service.login(new AuthRequest("nobody@example.com", "pw", "pw"), servletResponse));
    }

    // --- google native flow -----------------------------------------------




    // --- google callback --------------------------------------------------







}
