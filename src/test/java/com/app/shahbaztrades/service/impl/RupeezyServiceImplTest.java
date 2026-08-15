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
import com.app.shahbaztrades.repo.redis.RupeezyTokenCacheRedisRepo;
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
    private RupeezyTokenCacheRedisRepo<RupeezyTokenCache> rupeezyTokenCacheRedisRepo;

    private RupeezyServiceImpl service;

    private static final UserDto USER_DTO = UserDto.builder().userId(7L).build();

    @BeforeEach
    void setUp() {
        service = new RupeezyServiceImpl(rupeezyClient, userService, mongoTemplate, rupeezyTokenCacheRedisRepo);
        // The in-process cache is a static field on the interface: reset it between tests.
        RupeezyService.rupeezyTokenCache.invalidateAll();
    }

    @AfterEach
    void tearDown() {
        RupeezyService.rupeezyTokenCache.invalidateAll();
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

        assertEquals("tok-1", RupeezyService.rupeezyTokenCache.get(7L).getAccessToken());
        verify(rupeezyTokenCacheRedisRepo).set(eq("7"), any(RupeezyTokenCache.class), any(Duration.class));
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
        assertNull(RupeezyService.rupeezyTokenCache.get(7L));
    }

    @Test
    void login_throwsForAnUnknownUser() {
        stubUser(null);
        assertThrows(UnauthorizedException.class, () -> service.login(new BrokerLoginDto("t", 7L)));
    }

    // --- token cache ------------------------------------------------------

    @Test
    void getTokenCache_promotesTheRedisEntryIntoTheLocalCache() {
        var cached = RupeezyTokenCache.builder().apiSecret("secret").accessToken("tok-1").build();
        when(rupeezyTokenCacheRedisRepo.get("7")).thenReturn(cached);

        assertSame(cached, service.getTokenCache(7L));

        // A second read must be served locally, so Redis is hit exactly once.
        assertSame(cached, service.getTokenCache(7L));
        verify(rupeezyTokenCacheRedisRepo).get("7");
    }

    @Test
    void getTokenCache_returnsNullWhenNeitherLayerHasAToken() {
        when(rupeezyTokenCacheRedisRepo.get("7")).thenReturn(null);
        assertNull(service.getTokenCache(7L));
    }

    @Test
    void revokeRupeezyAuth_clearsBothLayers() {
        RupeezyService.rupeezyTokenCache.set(7L,
                RupeezyTokenCache.builder().accessToken("tok").build(), Duration.ofHours(1));

        service.revokeRupeezyAuth(7L);

        assertNull(RupeezyService.rupeezyTokenCache.get(7L));
        verify(rupeezyTokenCacheRedisRepo).delete("7");
    }

    // --- getAuth ----------------------------------------------------------

    @Test
    void getAuth_reportsSuccessWhenTheTokenStillWorks() {
        stubUser(user("app", "secret"));
        when(rupeezyTokenCacheRedisRepo.get("7"))
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
        when(rupeezyTokenCacheRedisRepo.get("7")).thenReturn(null);

        var response = service.getAuth(USER_DTO);

        assertFalse(response.isSuccess());
        assertEquals("app", response.getData());
        assertEquals("Token expired", response.getMessage());
    }

    @Test
    void getAuth_reportsExpiryWhenTheFundsCallFails() {
        stubUser(user("app", "secret"));
        when(rupeezyTokenCacheRedisRepo.get("7"))
                .thenReturn(RupeezyTokenCache.builder().apiSecret("secret").accessToken("tok").build());
        when(rupeezyClient.getUserFunds(anyString(), anyString())).thenThrow(new RuntimeException("401"));

        assertFalse(service.getAuth(USER_DTO).isSuccess());
    }

    @Test
    void getAuth_reportsExpiryWhenFundsComeBackWithoutAnNseSegment() {
        stubUser(user("app", "secret"));
        when(rupeezyTokenCacheRedisRepo.get("7"))
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
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(User.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        assertEquals(7L, service.setConfig(user("app", "secret").getRupeezyConfig(), USER_DTO));
    }

    @Test
    void setConfig_rejectsAnIncompleteConfig() {
        assertThrows(BadRequestException.class,
                () -> service.setConfig(user("app", "").getRupeezyConfig(), USER_DTO));
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(User.class));
    }

    @Test
    void setConfig_throwsWhenNoUserDocumentMatched() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(User.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        assertThrows(UnauthorizedException.class,
                () -> service.setConfig(user("app", "secret").getRupeezyConfig(), USER_DTO));
    }
}
