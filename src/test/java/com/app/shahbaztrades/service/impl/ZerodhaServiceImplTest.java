package com.app.shahbaztrades.service.impl;

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

@ExtendWith(MockitoExtension.class)
class ZerodhaServiceImplTest {

    @Mock
    private UserService userService;
    @Mock
    private ZerodhaClientFactory zerodhaClientFactory;
    @Mock
    private ZerodhaTokenStore zerodhaTokenStore;
    @Mock
    private ZerodhaAutoLoginLock autoLoginLock;

    private ZerodhaServiceImpl service;

    private static final UserDto USER_DTO = UserDto.builder().userId(7L).build();

    @BeforeEach
    void setUp() {
        service = new ZerodhaServiceImpl(userService, zerodhaClientFactory, zerodhaTokenStore, autoLoginLock);
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
    void login_throwsWhenTheBrokerIsNotConfigured() {
        stubUser(user(null));
        assertThrows(BadRequestException.class, () -> service.login(new BrokerLoginDto("req", 7L)));
    }

    // --- session lifecycle ------------------------------------------------

    @Test
    void revokeAuth_clearsBothTheTokenStoreAndTheCachedClient() {
        service.revokeAuth(7L);

        verify(zerodhaTokenStore).delete(7L);
        verify(zerodhaClientFactory).evict(7L);
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
        when(autoLoginLock.isPending(7L)).thenReturn(true);

        assertThrows(ResourceAlreadyExistsException.class, () -> service.getAuth(USER_DTO));
    }

    @Test
    void getAuth_reportsTokenExpiredWhenNoAccessTokenExists() {
        stubUser(user(config(false)));
        when(zerodhaClientFactory.forUser(7L)).thenThrow(new NotFoundException("Access token not found"));

        var response = service.getAuth(USER_DTO);

        assertEquals(false, response.isSuccess());
        assertEquals("key", response.getData(), "the api key drives the broker re-login redirect");
    }

    // --- setConfig --------------------------------------------------------

    @Test
    void setConfig_persistsAndReturnsTheUserId() {
        when(userService.updateZerodhaConfig(eq(7L), any(User.ZerodhaConfig.class))).thenReturn(true);

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
        when(userService.updateZerodhaConfig(eq(7L), any(User.ZerodhaConfig.class))).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> service.setConfig(config(false), USER_DTO));
    }

    // --- auto login -------------------------------------------------------







    // --- callback ---------------------------------------------------------




}
