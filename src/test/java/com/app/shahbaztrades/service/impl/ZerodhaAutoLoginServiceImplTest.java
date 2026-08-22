package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.sessionmanager.SessionManagerClient;
import com.app.shahbaztrades.components.zerodha.ZerodhaAutoLoginLock;
import com.app.shahbaztrades.components.zerodha.ZerodhaClientFactory;
import com.app.shahbaztrades.components.zerodha.ZerodhaTokenStore;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.zerodha.BrokerLoginDto;
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
import com.app.shahbaztrades.components.zerodha.ZerodhaAutoLoginLock;
import com.app.shahbaztrades.components.zerodha.ZerodhaClientFactory;
import com.app.shahbaztrades.service.ZerodhaService;
import org.springframework.core.task.support.TaskExecutorAdapter;

/** Unattended Zerodha login, split out of ZerodhaServiceImplTest alongside the production split. */
@ExtendWith(MockitoExtension.class)
class ZerodhaAutoLoginServiceImplTest {

    @Mock
    private UserService userService;
    @Mock
    private ZerodhaService zerodhaService;
    @Mock
    private ZerodhaClientFactory zerodhaClientFactory;
    @Mock
    private ZerodhaAutoLoginLock autoLoginLock;
    @Mock
    private SessionManagerClient sessionManagerClient;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private ZerodhaAutoLoginServiceImpl service;

    private static final UserDto USER_DTO = UserDto.builder().userId(7L).build();

    @BeforeEach
    void setUp() {
        // Run the background hand-off inline so the tests observe its effects deterministically.
        service = new ZerodhaAutoLoginServiceImpl(userService, zerodhaService, zerodhaClientFactory,
                autoLoginLock, sessionManagerClient, cacheManager, applicationEventPublisher,
                new TaskExecutorAdapter(Runnable::run));
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
        verify(autoLoginLock).release(7L);
    }

    @Test
    void autoConnectZerodhaSession_refusesAndClearsTheFlagWhenAutoLoginIsDisabled() {
        assertThrows(BadRequestException.class, () -> service.autoConnectZerodhaSession(user(config(false))));

        verify(autoLoginLock).release(7L);
        verify(sessionManagerClient, never()).autoLogin(any(ZerodhaLoginRequestDTO.class), anyString());
    }

    @Test
    void sessionManagerCallback_clearsStateEvenWhenTheLoginItselfBlowsUp() {
        // The user has no broker config, so login() throws; the finally block must still run.
        stubUser(user(null));
        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("zerodhaAuthCache")).thenReturn(cache);

        service.sessionManagerCallback(ZerodhaLoginResponseDTO.builder()
                .status("SUCCESS").userid(7L).requestToken("req-token").build());

        verify(autoLoginLock).release(7L);
        verify(cache).evict(7L);
    }

    @Test
    void sessionManagerCallback_skipsLoginWhenTheRequestTokenIsBlank() {
        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("zerodhaAuthCache")).thenReturn(cache);

        service.sessionManagerCallback(ZerodhaLoginResponseDTO.builder()
                .status("SUCCESS").userid(7L).requestToken("").build());

        verify(userService, never()).findByUserIdOrEmailOrMobile(anyLong(), anyString(), anyLong());
        verify(autoLoginLock).release(7L);
    }

    @Test
    void sessionManagerCallback_stillClearsStateOnAnErrorPayload() {
        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("zerodhaAuthCache")).thenReturn(cache);

        service.sessionManagerCallback(ZerodhaLoginResponseDTO.builder()
                .status("ERROR").userid(7L).build());

        // No login is attempted, but the in-flight flag must not be left behind.
        verify(autoLoginLock).release(7L);
        verify(cache).evict(7L);
    }

    @Test
    void sessionManagerCallback_toleratesAMissingCacheBean() {
        when(cacheManager.getCache("zerodhaAuthCache")).thenReturn(null);

        service.sessionManagerCallback(ZerodhaLoginResponseDTO.builder()
                .status("ERROR").userid(7L).build());

        verify(autoLoginLock).release(7L);
    }
}
