package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.rupeezy.RupeezyClient;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezySessionRequest;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezySessionResponse;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezyTokenCache;
import com.app.shahbaztrades.model.dto.zerodha.BrokerLoginDto;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.components.rupeezy.RupeezyTokenStore;
import com.app.shahbaztrades.service.RupeezyService;
import com.app.shahbaztrades.service.UserService;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RupeezyServiceImplTest {

    @Mock
    private RupeezyClient rupeezyClient;
    @Mock
    private UserService userService;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private RupeezyTokenStore rupeezyTokenStore;

    private RupeezyServiceImpl service;

    private static final UserDto USER_DTO = UserDto.builder().userId(7L).build();

    @BeforeEach
    void setUp() {
        service = new RupeezyServiceImpl(rupeezyClient, userService, rupeezyTokenStore);
    }

    private User user(String appId, String apiSecret) {
        var config = new User.RupeezyConfig();
        config.setAppId(appId);
        config.setApiSecret(apiSecret);
        return User.builder().userId(7L).rupeezyConfig(config).build();
    }

    private void stubUser(User user) {
        lenient().when(userService.findByUserIdOrEmailOrMobile(anyLong(), anyString(), anyLong())).thenReturn(user);
    }

    private RupeezySessionResponse session(String status, String accessToken) {
        var response = new RupeezySessionResponse();
        response.setStatus(status);
        response.setData(RupeezySessionResponse.SessionData.builder().accessToken(accessToken).build());
        return response;
    }

    // --- login ------------------------------------------------------------

    @Test
    void login_cachesTheAccessTokenInBothMemoryAndRedis() {
        stubUser(user("app", "secret"));
        when(rupeezyClient.generateAccessToken(any(RupeezySessionRequest.class)))
                .thenReturn(session("success", "tok-1"));

        service.login(new BrokerLoginDto("request-token", 7L));

        verify(rupeezyTokenStore).save(eq(7L), any(RupeezyTokenCache.class));
    }

    @Test
    void login_signsTheRequestWithTheUsersApiSecret() {
        stubUser(user("app", "secret"));
        when(rupeezyClient.generateAccessToken(any(RupeezySessionRequest.class)))
                .thenReturn(session("success", "tok-1"));

        service.login(new BrokerLoginDto("request-token", 7L));

        var captor = org.mockito.ArgumentCaptor.forClass(RupeezySessionRequest.class);
        verify(rupeezyClient).generateAccessToken(captor.capture());
        assertEquals("app", captor.getValue().getApplicationId());
        assertEquals("request-token", captor.getValue().getToken());
        assertNotNullChecksum(captor.getValue());
    }

    private void assertNotNullChecksum(RupeezySessionRequest request) {
        assertTrue(request.getChecksum() != null && !request.getChecksum().isBlank(),
                "an unsigned session request is rejected by the broker");
    }

    @Test
    void login_throwsWhenTheBrokerRefusesTheRequestToken() {
        stubUser(user("app", "secret"));
        when(rupeezyClient.generateAccessToken(any(RupeezySessionRequest.class)))
                .thenReturn(session("error", null));

        assertThrows(NotFoundException.class, () -> service.login(new BrokerLoginDto("bad", 7L)));
        verify(rupeezyTokenStore, org.mockito.Mockito.never()).save(anyLong(), any(RupeezyTokenCache.class));
    }

    @Test
    void login_throwsForAnUnknownUser() {
        stubUser(null);
        assertThrows(UnauthorizedException.class, () -> service.login(new BrokerLoginDto("t", 7L)));
    }

    // --- token cache ------------------------------------------------------




    // --- getAuth ----------------------------------------------------------

    @Test
    void getAuth_reportsSuccessWhenTheTokenStillWorks() {
        stubUser(user("app", "secret"));
        when(rupeezyTokenStore.find(7L))
                .thenReturn(RupeezyTokenCache.builder().apiSecret("secret").accessToken("tok").build());
        when(rupeezyClient.getUserFunds(anyString(), anyString())).thenReturn(Map.of("nse", Map.of()));

        var response = service.getAuth(USER_DTO);

        assertTrue(response.isSuccess());
        assertEquals("7", response.getData());
    }

    @Test
    void getAuth_reportsExpiryAndReturnsTheAppIdWhenThereIsNoToken() {
        // The frontend uses the returned appId to build the re-login URL.
        stubUser(user("app", "secret"));
        when(rupeezyTokenStore.find(7L)).thenReturn(null);

        var response = service.getAuth(USER_DTO);

        assertFalse(response.isSuccess());
        assertEquals("app", response.getData());
        assertEquals("Token expired", response.getMessage());
    }

    @Test
    void getAuth_reportsExpiryWhenTheFundsCallFails() {
        stubUser(user("app", "secret"));
        when(rupeezyTokenStore.find(7L))
                .thenReturn(RupeezyTokenCache.builder().apiSecret("secret").accessToken("tok").build());
        when(rupeezyClient.getUserFunds(anyString(), anyString())).thenThrow(new RuntimeException("401"));

        assertFalse(service.getAuth(USER_DTO).isSuccess());
    }

    @Test
    void getAuth_reportsExpiryWhenFundsComeBackWithoutAnNseSegment() {
        stubUser(user("app", "secret"));
        when(rupeezyTokenStore.find(7L))
                .thenReturn(RupeezyTokenCache.builder().apiSecret("secret").accessToken("tok").build());
        when(rupeezyClient.getUserFunds(anyString(), anyString())).thenReturn(Map.of());

        assertFalse(service.getAuth(USER_DTO).isSuccess());
    }

    @Test
    void getAuth_throwsWhenTheBrokerWasNeverConfigured() {
        stubUser(User.builder().userId(7L).build());
        assertThrows(NotFoundException.class, () -> service.getAuth(USER_DTO));
    }

    // --- setConfig --------------------------------------------------------

    @Test
    void setConfig_persistsAndReturnsTheUserId() {
        when(userService.updateRupeezyConfig(eq(7L), any(User.RupeezyConfig.class))).thenReturn(true);

        assertEquals(7L, service.setConfig(user("app", "secret").getRupeezyConfig(), USER_DTO));
    }

    @Test
    void setConfig_rejectsAnIncompleteConfig() {
        assertThrows(BadRequestException.class,
                () -> service.setConfig(user("app", "").getRupeezyConfig(), USER_DTO));
        verify(userService, never()).updateRupeezyConfig(anyLong(), any(User.RupeezyConfig.class));
    }

    @Test
    void setConfig_throwsWhenNoUserDocumentMatched() {
        when(userService.updateRupeezyConfig(eq(7L), any(User.RupeezyConfig.class))).thenReturn(false);

        assertThrows(UnauthorizedException.class,
                () -> service.setConfig(user("app", "secret").getRupeezyConfig(), USER_DTO));
    }
}
