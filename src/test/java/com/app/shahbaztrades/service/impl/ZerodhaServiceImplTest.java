package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.sessionmanager.SessionManagerClient;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.fcm.NotificationRequest;
import com.app.shahbaztrades.model.dto.sessionmanager.ZerodhaLoginRequestDTO;
import com.app.shahbaztrades.model.dto.sessionmanager.ZerodhaLoginResponseDTO;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.service.ZerodhaService;
import com.app.shahbaztrades.util.Constants;
import com.mongodb.client.result.UpdateResult;
import com.zerodhatech.kiteconnect.KiteConnect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZerodhaServiceImplTest {

    @Mock
    private UserService userService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private SessionManagerClient sessionManagerClient;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private ZerodhaServiceImpl service;

    private static final UserDto USER_DTO = UserDto.builder().userId(7L).build();

    @BeforeEach
    void setUp() {
        service = new ZerodhaServiceImpl(userService, stringRedisTemplate, mongoTemplate,
                sessionManagerClient, cacheManager, applicationEventPublisher);
        ZerodhaService.kiteClientCache.invalidateAll();
    }

    @AfterEach
    void tearDown() {
        ZerodhaService.kiteClientCache.invalidateAll();
    }

    private User.ZerodhaConfig config(boolean withTotp) {
        var config = new User.ZerodhaConfig();
        config.setApiKey("key");
        config.setApiSecret("secret");
        if (withTotp) {
            config.setUserName("kite-user");
            config.setPassword("pw");
            config.setTotpSecret("JBSWY3DPEHPK3PXP");
        }
        return config;
    }

    private User user(User.ZerodhaConfig config) {
        return User.builder().userId(7L).zerodhaConfig(config).build();
    }

    private void stubUser(User user) {
        lenient().when(userService.findByUserIdOrEmailOrMobile(anyLong(), anyString(), anyLong())).thenReturn(user);
    }

    // --- client construction ---------------------------------------------

    @Test
    void initiateKiteConnect_buildsAClientFromTheUsersApiKey() {
        stubUser(user(config(false)));

        KiteConnect kc = service.initiateKiteConnect("access-token", 7L);

        assertNotNull(kc);
        assertEquals("access-token", kc.getAccessToken());
    }

    @Test
    void initiateKiteConnect_throwsWhenTheBrokerIsNotConfigured() {
        stubUser(user(null));
        assertThrows(NotFoundException.class, () -> service.initiateKiteConnect("t", 7L));
    }

    @Test
    void initiateKiteConnect_throwsForAnUnknownUser() {
        stubUser(null);
        assertThrows(UnauthorizedException.class, () -> service.initiateKiteConnect("t", 7L));
    }

    @Test
    void generateAccessToken_throwsWhenTheBrokerIsNotConfigured() {
        stubUser(user(null));
        assertThrows(BadRequestException.class, () -> service.generateAccessToken("req", 7L));
    }

    // --- client cache -----------------------------------------------------

    @Test
    void getKiteClient_servesTheProcessCacheWithoutHittingRedis() {
        KiteConnect cached = new KiteConnect("key");
        ZerodhaService.kiteClientCache.set(7L, cached, Duration.ofHours(1));

        assertSame(cached, service.getKiteClient(7L));

        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    void getKiteClient_buildsAndCachesFromTheRedisAccessToken() {
        stubUser(user(config(false)));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ZerodhaService.ZERODHA_TOKEN_KEY + 7L)).thenReturn("access-token");

        KiteConnect kc = service.getKiteClient(7L);

        assertEquals("access-token", kc.getAccessToken());
        assertSame(kc, ZerodhaService.kiteClientCache.get(7L));
    }

    @Test
    void getKiteClient_throwsWhenNoAccessTokenIsStored() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThrows(NotFoundException.class, () -> service.getKiteClient(7L));
    }

    @Test
    void revokeZerodhaAuth_clearsBothRedisAndTheProcessCache() {
        ZerodhaService.kiteClientCache.set(7L, new KiteConnect("key"), Duration.ofHours(1));

        service.revokeZerodhaAuth(7L);

        assertNull(ZerodhaService.kiteClientCache.get(7L));
        verify(stringRedisTemplate).delete(ZerodhaService.ZERODHA_TOKEN_KEY + 7L);
    }

    // --- getAuth ----------------------------------------------------------

    @Test
    void getAuth_throwsWhenTheBrokerIsNotConfigured() {
        stubUser(user(null));
        assertThrows(NotFoundException.class, () -> service.getAuth(USER_DTO));
    }

    @Test
    void getAuth_reportsAConflictWhileAnAutoLoginIsAlreadyInFlight() {
        // Two concurrent auto-logins would burn the single-use TOTP code.
        stubUser(user(config(true)));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(Constants.ZERODHA_AUTO_LOGIN_KEY + 7L)).thenReturn("PENDING");

        assertThrows(ResourceAlreadyExistsException.class, () -> service.getAuth(USER_DTO));
    }

    @Test
    void getAuth_reportsTokenExpiredWhenNoAccessTokenExists() {
        stubUser(user(config(false)));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ZerodhaService.ZERODHA_TOKEN_KEY + 7L)).thenReturn(null);

        var response = service.getAuth(USER_DTO);

        assertEquals(false, response.isSuccess());
        assertEquals("key", response.getData(), "the api key drives the broker re-login redirect");
    }

    // --- setConfig --------------------------------------------------------

    @Test
    void setConfig_persistsAndReturnsTheUserId() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(User.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        assertEquals(7L, service.setConfig(config(false), USER_DTO));
    }

    @Test
    void setConfig_rejectsAnIncompleteApiConfig() {
        var config = new User.ZerodhaConfig();
        config.setApiKey("key");

        assertThrows(BadRequestException.class, () -> service.setConfig(config, USER_DTO));
    }

    @Test
    void setConfig_rejectsAUsernameWithoutPasswordAndTotp() {
        // Half-configured auto-login would fail silently every morning.
        var config = config(false);
        config.setUserName("kite-user");

        assertThrows(BadRequestException.class, () -> service.setConfig(config, USER_DTO));
    }

    @Test
    void setConfig_throwsWhenNoUserDocumentMatched() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(User.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        assertThrows(UnauthorizedException.class, () -> service.setConfig(config(false), USER_DTO));
    }

    // --- auto login -------------------------------------------------------

    @Test
    void autoLogin_notifiesUsersWhoCannotLogInUnattended() {
        when(userService.findByIds(Set.of(7L))).thenReturn(List.of(user(config(false))));

        service.autoLogin(Set.of(7L));

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(event.capture());
        assertEquals(7L, ((NotificationRequest) event.getValue()).userId());
    }

    @Test
    void autoLogin_isANoOpWhenNoUsersResolve() {
        when(userService.findByIds(Set.of(7L))).thenReturn(List.of());

        service.autoLogin(Set.of(7L));

        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void autoConnectZerodhaSession_delegatesToTheSessionManager() {
        when(sessionManagerClient.autoLogin(any(ZerodhaLoginRequestDTO.class), eq(SessionManagerClient.SOURCE)))
                .thenReturn(ZerodhaLoginResponseDTO.builder().status("PENDING").message("queued").build());

        service.autoConnectZerodhaSession(user(config(true)));

        verify(sessionManagerClient).autoLogin(any(ZerodhaLoginRequestDTO.class), eq(SessionManagerClient.SOURCE));
    }

    @Test
    void autoConnectZerodhaSession_reportsAConflictWhenGenerationIsAlreadyRunning() {
        when(sessionManagerClient.autoLogin(any(ZerodhaLoginRequestDTO.class), anyString()))
                .thenReturn(ZerodhaLoginResponseDTO.builder()
                        .status("PENDING").message("Token generation already in progress").build());

        assertThrows(ResourceAlreadyExistsException.class,
                () -> service.autoConnectZerodhaSession(user(config(true))));
    }

    @Test
    void autoConnectZerodhaSession_clearsTheInFlightFlagWhenTheSessionManagerErrors() {
        // Leaving the flag set would lock the user out for the full three-minute TTL.
        when(sessionManagerClient.autoLogin(any(ZerodhaLoginRequestDTO.class), anyString()))
                .thenReturn(ZerodhaLoginResponseDTO.builder().status("ERROR").message("boom").build());

        assertThrows(BadRequestException.class, () -> service.autoConnectZerodhaSession(user(config(true))));
        verify(stringRedisTemplate).delete(Constants.ZERODHA_AUTO_LOGIN_KEY + 7L);
    }

    @Test
    void autoConnectZerodhaSession_refusesAndClearsTheFlagWhenAutoLoginIsDisabled() {
        assertThrows(BadRequestException.class, () -> service.autoConnectZerodhaSession(user(config(false))));

        verify(stringRedisTemplate).delete(Constants.ZERODHA_AUTO_LOGIN_KEY + 7L);
        verify(sessionManagerClient, never()).autoLogin(any(ZerodhaLoginRequestDTO.class), anyString());
    }

    // --- callback ---------------------------------------------------------

    @Test
    void sessionManagerCallback_clearsStateEvenWhenTheLoginItselfBlowsUp() {
        // The user has no broker config, so login() throws; the finally block must still run.
        stubUser(user(null));
        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("zerodhaAuthCache")).thenReturn(cache);

        service.sessionManagerCallback(ZerodhaLoginResponseDTO.builder()
                .status("SUCCESS").userid(7L).requestToken("req-token").build());

        verify(stringRedisTemplate).delete(Constants.ZERODHA_AUTO_LOGIN_KEY + 7L);
        verify(cache).evict(7L);
    }

    @Test
    void sessionManagerCallback_skipsLoginWhenTheRequestTokenIsBlank() {
        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("zerodhaAuthCache")).thenReturn(cache);

        service.sessionManagerCallback(ZerodhaLoginResponseDTO.builder()
                .status("SUCCESS").userid(7L).requestToken("").build());

        verify(userService, never()).findByUserIdOrEmailOrMobile(anyLong(), anyString(), anyLong());
        verify(stringRedisTemplate).delete(Constants.ZERODHA_AUTO_LOGIN_KEY + 7L);
    }

    @Test
    void sessionManagerCallback_stillClearsStateOnAnErrorPayload() {
        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("zerodhaAuthCache")).thenReturn(cache);

        service.sessionManagerCallback(ZerodhaLoginResponseDTO.builder()
                .status("ERROR").userid(7L).build());

        // No login is attempted, but the in-flight flag must not be left behind.
        verify(stringRedisTemplate).delete(Constants.ZERODHA_AUTO_LOGIN_KEY + 7L);
        verify(cache).evict(7L);
        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    void sessionManagerCallback_toleratesAMissingCacheBean() {
        when(cacheManager.getCache("zerodhaAuthCache")).thenReturn(null);

        service.sessionManagerCallback(ZerodhaLoginResponseDTO.builder()
                .status("ERROR").userid(7L).build());

        verify(stringRedisTemplate).delete(Constants.ZERODHA_AUTO_LOGIN_KEY + 7L);
    }
}
