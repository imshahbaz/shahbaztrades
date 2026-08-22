package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.angelone.AngelOneClient;
import com.app.shahbaztrades.components.angelone.SmartApiFeignClient;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;
import com.app.shahbaztrades.model.dto.angelone.websocket.AngelOneLoginResponse;
import com.app.shahbaztrades.model.entity.ServerConfigurations;
import com.app.shahbaztrades.repo.redis.AngelOneLoginDataRedisRepo;
import com.app.shahbaztrades.service.MongoConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Broker credentials and their renewal. */
@ExtendWith(MockitoExtension.class)
class AngelOneSessionServiceTest {

    @Mock
    private AngelOneClient angelOneClient;
    @Mock
    private MongoConfigService mongoConfigService;
    @Mock
    private SmartApiFeignClient smartApiFeignClient;
    @Mock
    private AngelOneLoginDataRedisRepo<AngelOneLoginResponse.LoginData> angelOneLoginDataRedisRepo;

    private AngelOneSessionService service;

    @BeforeEach
    void setUp() {
        service = new AngelOneSessionService(angelOneClient, mongoConfigService, smartApiFeignClient,
                angelOneLoginDataRedisRepo);
    }

    private void stubConfig() {
        var angelOne = new ServerConfigurations.AngelOneConfig();
        angelOne.setApiKey("api-key");
        angelOne.setClientId("client");
        var config = new ServerConfigurations();
        config.setAngelOneConfig(angelOne);
        lenient().when(mongoConfigService.getConfig()).thenReturn(config);
    }

    private AngelOneLoginResponse.LoginData loginData(String jwt, String feed) {
        var data = new AngelOneLoginResponse.LoginData();
        data.setJwtToken(jwt);
        data.setFeedToken(feed);
        return data;
    }

    @Test
    void credentialsAreReadThroughFromTheStoredConfig() {
        stubConfig();
        when(mongoConfigService.getAngelOneJwtToken()).thenReturn("jwt");
        when(mongoConfigService.getAngelOneFeedToken()).thenReturn("feed");

        assertEquals("jwt", service.jwtToken());
        assertEquals("feed", service.feedToken());
        assertEquals("api-key", service.apiKey());
        assertEquals("client", service.clientId());
    }

    @Test
    void refreshBrokerSession_reusesACachedTokenThatStillValidates() {
        stubConfig();
        when(angelOneLoginDataRedisRepo.get("oneklik")).thenReturn(loginData("jwt-1", "feed-1"));
        when(smartApiFeignClient.getUserProfile(anyString(), eq("api-key")))
                .thenReturn(new SmartApiLtpResponse<>(true, "ok", null, new Object()));

        service.refreshBrokerSession();

        verify(mongoConfigService).setAngelOneJwtToken("jwt-1");
        verify(mongoConfigService).setAngelOneFeedToken("feed-1");
        // A fresh TOTP login burns a one-time code, so it must be avoided when possible.
        verify(angelOneClient, never()).getWebsocketLogin(any());
    }

    @Test
    void refreshBrokerSession_reLogsInWhenTheCachedTokenIsRejected() {
        stubConfig();
        when(angelOneLoginDataRedisRepo.get("oneklik")).thenReturn(loginData("stale", "feed"));
        when(smartApiFeignClient.getUserProfile(anyString(), anyString()))
                .thenReturn(new SmartApiLtpResponse<>(false, "Invalid Token", "AG8001", null));
        when(angelOneClient.getWebsocketLogin(any())).thenReturn(loginData("jwt-2", "feed-2"));

        service.refreshBrokerSession();

        verify(mongoConfigService).setAngelOneJwtToken("jwt-2");
        verify(angelOneLoginDataRedisRepo).set(eq("oneklik"),
                any(AngelOneLoginResponse.LoginData.class), any(Duration.class));
    }

    @Test
    void refreshBrokerSession_logsInFreshWhenRedisHasNothing() {
        stubConfig();
        when(angelOneLoginDataRedisRepo.get("oneklik")).thenReturn(null);
        when(angelOneClient.getWebsocketLogin(any())).thenReturn(loginData("jwt-3", "feed-3"));

        service.refreshBrokerSession();

        verify(mongoConfigService).setAngelOneJwtToken("jwt-3");
        verify(smartApiFeignClient, never()).getUserProfile(anyString(), anyString());
    }

    @Test
    void refreshBrokerSession_leavesTokensAloneWhenTheLoginFails() {
        stubConfig();
        when(angelOneLoginDataRedisRepo.get("oneklik")).thenReturn(null);
        when(angelOneClient.getWebsocketLogin(any())).thenReturn(null);

        service.refreshBrokerSession();

        verify(mongoConfigService, never()).setAngelOneJwtToken(anyString());
    }
}
