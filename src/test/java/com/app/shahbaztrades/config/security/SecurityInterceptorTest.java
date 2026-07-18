package com.app.shahbaztrades.config.security;

import com.app.shahbaztrades.exceptions.ForbiddenException;
import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.JwtClaims;
import com.app.shahbaztrades.model.enums.UserRole;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.web.method.HandlerMethod;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityInterceptorTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private Environment environment;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private HandlerMethod handlerMethod;

    private SecurityInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new SecurityInterceptor(jwtService, environment);
    }

    private void stubValidToken(UserRole role) {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("auth_token", "tok")});
        // Far-future expiry so the rolling-refresh path is not triggered.
        long expiresAt = System.currentTimeMillis() + Duration.ofHours(24).toMillis();
        UserDto user = UserDto.builder().userId(7L).role(role).build();
        when(jwtService.validateToken("tok"))
                .thenReturn(JwtClaims.builder().user(user).expiresAt(expiresAt).build());
    }

    @Test
    void nonHandlerMethod_isAllowed() {
        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void publicEndpoint_skipsAuth() {
        when(handlerMethod.hasMethodAnnotation(PublicEndpoint.class)).thenReturn(true);
        assertTrue(interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void missingCookies_isUnauthorized() {
        when(handlerMethod.hasMethodAnnotation(PublicEndpoint.class)).thenReturn(false);
        when(request.getCookies()).thenReturn(null);
        assertThrows(UnauthorizedException.class,
                () -> interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void validUserOnNonAdminEndpoint_isAllowedAndSetsUserAttribute() {
        lenient().when(handlerMethod.hasMethodAnnotation(PublicEndpoint.class)).thenReturn(false);
        lenient().when(handlerMethod.hasMethodAnnotation(AdminOnly.class)).thenReturn(false);
        stubValidToken(UserRole.USER);

        assertTrue(interceptor.preHandle(request, response, handlerMethod));
        verify(request).setAttribute(eq("user"), org.mockito.ArgumentMatchers.any(UserDto.class));
    }

    @Test
    void nonAdminOnAdminEndpoint_isForbidden() {
        lenient().when(handlerMethod.hasMethodAnnotation(PublicEndpoint.class)).thenReturn(false);
        when(handlerMethod.hasMethodAnnotation(AdminOnly.class)).thenReturn(true);
        stubValidToken(UserRole.USER);

        assertThrows(ForbiddenException.class,
                () -> interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void adminOnAdminEndpoint_isAllowed() {
        lenient().when(handlerMethod.hasMethodAnnotation(PublicEndpoint.class)).thenReturn(false);
        when(handlerMethod.hasMethodAnnotation(AdminOnly.class)).thenReturn(true);
        stubValidToken(UserRole.ADMIN);

        assertTrue(interceptor.preHandle(request, response, handlerMethod));
    }
}
