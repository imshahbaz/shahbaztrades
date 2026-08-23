package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.model.entity.ServerConfigurations;
import com.app.shahbaztrades.components.auth.GoogleAuthUtils;
import com.app.shahbaztrades.components.auth.SessionCookieIssuer;
import com.app.shahbaztrades.config.security.JwtService;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.AuthRequest;
import com.app.shahbaztrades.model.dto.auth.GoogleUser;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.model.enums.UserRole;
import com.app.shahbaztrades.repo.redis.AuthDataRedisRepo;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.util.AuthUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The Google sign-in flows, split out of AuthServiceImplTest alongside the production split. */
@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceImplTest {

    private static final String ENCRYPTION_KEY = "state-signing-key";

    @Mock
    private UserService userService;
    @Mock
    private MongoConfigService mongoConfigService;
    @Mock
    private GoogleAuthUtils googleAuthUtils;
    @Mock
    private JwtService jwtService;
    @Mock
    private SessionCookieIssuer sessionCookieIssuer;
    @Mock
    private AuthDataRedisRepo<UserDto> authDataRedisRepo;
    @Mock
    private AuthDataRedisRepo<User> userAuthDataRedisRepo;

    private GoogleAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        // Run the async hand-off inline so the tests observe its effects deterministically.
        service = new GoogleAuthServiceImpl(userService, mongoConfigService, googleAuthUtils, jwtService,
                sessionCookieIssuer, authDataRedisRepo, userAuthDataRedisRepo,
                new org.springframework.core.task.support.TaskExecutorAdapter(Runnable::run));
    }

    private void stubConfig(String... frontendUrls) {
        var googleAuth = new ServerConfigurations.GoogleAuthCredentials();
        googleAuth.setEncryptionKey(ENCRYPTION_KEY);
        googleAuth.setClientId("client-id");
        var config = new ServerConfigurations();
        config.setGoogleAuth(googleAuth);
        config.setFrontendUrls(List.of(frontendUrls));
        lenient().when(mongoConfigService.getConfig()).thenReturn(config);
    }

    private User user() {
        return User.builder().userId(7L).email("jane@example.com").role(UserRole.USER)
                .password(AuthUtil.ENCODER.encode("pw")).build();
    }

    // --- logout / me ------------------------------------------------------

    @Test
    void validateGoogleToken_nativeFlowIssuesAJwtAndCookieImmediately() {
        stubConfig();
        var gUser = GoogleUser.builder().email("jane@example.com").name("Jane").build();
        when(googleAuthUtils.validateIdToken("id-token")).thenReturn(gUser);
        when(userService.findOrCreateGoogleUser(gUser)).thenReturn(user());
        when(jwtService.generateToken(any(UserDto.class))).thenReturn("jwt-token");
        when(sessionCookieIssuer.issue("jwt-token")).thenReturn("auth_token=jwt-token; Path=/");

        var response = service.validateGoogleToken("id-token", true);

        assertEquals("jwt-token", response.data());
        assertTrue(response.cookie().contains("auth_token=jwt-token"));
    }

    @Test
    void validateGoogleToken_nativeFlowRejectsAnInvalidIdToken() {
        when(googleAuthUtils.validateIdToken("bad")).thenReturn(null);

        assertThrows(BadRequestException.class, () -> service.validateGoogleToken("bad", true));
    }

    @Test
    void validateGoogleToken_webFlowReturnsASignedHandleAndNoCookieYet() {
        // The token exchange runs in the background; the caller polls with this signed handle.
        stubConfig();

        var response = service.validateGoogleToken("code", false);

        assertNull(response.cookie());
        assertNotNull(AuthUtil.extractAndVerify(response.data(), ENCRYPTION_KEY),
                "the handle must be verifiable with the configured key");
    }

    @Test
    void googleAuthCallback_rejectsAnUnknownState() {
        assertThrows(UnauthorizedException.class, () -> service.googleAuthCallback("code", "bogus"));
        assertThrows(UnauthorizedException.class, () -> service.googleAuthCallback("code", null));
    }

    @Test
    void googleAuthCallback_redirectsOnlyToAWhitelistedFrontend() {
        stubConfig("https://app.example.com");

        var response = service.googleAuthCallback("code", "redirect|https://app.example.com");

        assertTrue(response.isRedirect());
        assertTrue(response.redirectUrl().startsWith("https://app.example.com/google/callback?code="));
    }

    @Test
    void googleAuthCallback_refusesAnOpenRedirectToAnUnknownOrigin() {
        // Otherwise an attacker could have the signed session handle delivered to their own site.
        stubConfig("https://app.example.com");

        assertThrows(BadRequestException.class,
                () -> service.googleAuthCallback("code", "redirect|https://evil.example.com"));
    }

    @Test
    void googleAuthCallback_rejectsAMalformedRedirectState() {
        stubConfig("https://app.example.com");

        assertThrows(UnauthorizedException.class,
                () -> service.googleAuthCallback("code", "redirect|a|b"));
    }

    @Test
    void googleAuthCallback_standardStateRejectsATamperedHandle() {
        stubConfig();

        assertThrows(BadRequestException.class, () -> service.googleAuthCallback("not-signed", "standard"));
    }

    @Test
    void googleAuthCallback_standardStateReportsWhenTheBackgroundExchangeHasNotFinished() {
        stubConfig();
        String signed = AuthUtil.signState("session-id", ENCRYPTION_KEY);
        when(userAuthDataRedisRepo.get("session-id")).thenReturn(null);

        assertThrows(NotFoundException.class, () -> service.googleAuthCallback(signed, "standard"));
    }

    @Test
    void googleAuthCallback_standardStateIssuesTheSessionAndConsumesTheHandle() {
        stubConfig();
        String signed = AuthUtil.signState("session-id", ENCRYPTION_KEY);
        when(userAuthDataRedisRepo.get("session-id")).thenReturn(user());
        when(jwtService.generateToken(any(UserDto.class))).thenReturn("jwt-token");
        when(sessionCookieIssuer.issue("jwt-token")).thenReturn("auth_token=jwt-token; Path=/");

        var response = service.googleAuthCallback(signed, "standard");

        assertTrue(response.cookie().contains("auth_token=jwt-token"));
        assertEquals(7L, response.user().getUserId());
        verify(authDataRedisRepo).set(eq("7"), any(UserDto.class), eq(Duration.ofHours(1)));
        // The one-time handle must not be replayable.
        verify(userAuthDataRedisRepo).delete("session-id");
    }
}
