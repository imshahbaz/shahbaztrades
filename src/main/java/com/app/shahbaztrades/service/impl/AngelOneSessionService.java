package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.angelone.AngelOneClient;
import com.app.shahbaztrades.components.angelone.SmartApiFeignClient;
import com.app.shahbaztrades.model.dto.angelone.websocket.AngelOneLoginResponse;
import com.app.shahbaztrades.repo.redis.AngelOneLoginDataRedisRepo;
import com.app.shahbaztrades.service.BrokerSession;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;

import static com.app.shahbaztrades.util.Constants.BEARER_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class AngelOneSessionService implements BrokerSession {

    private static final String LOGIN_CACHE_KEY = "oneklik";

    private final AngelOneClient angelOneClient;
    private final MongoConfigService mongoConfigService;
    private final SmartApiFeignClient smartApiFeignClient;
    private final AngelOneLoginDataRedisRepo<AngelOneLoginResponse.LoginData> angelOneLoginDataRedisRepo;

    /** Held in memory for the session; re-established on startup and whenever the broker rejects them. */
    private volatile String jwtToken;
    private volatile String feedToken;

    @Override
    @EventListener(ApplicationReadyEvent.class)
    public void refreshBrokerSession() {
        AngelOneLoginResponse.LoginData loginData = angelOneLoginDataRedisRepo.get(LOGIN_CACHE_KEY);
        if (loginData != null) {
            var response = smartApiFeignClient.getUserProfile(BEARER_PREFIX + loginData.getJwtToken(), apiKey());
            if (response != null && response.status() != null && response.status()) {
                storeTokens(loginData);
                return;
            }
        }

        loginData = angelOneClient.getWebsocketLogin(mongoConfigService.getConfig().getAngelOneConfig());
        if (loginData != null) {
            storeTokens(loginData);
            angelOneLoginDataRedisRepo.set(LOGIN_CACHE_KEY, loginData,
                    Duration.ofSeconds(DateUtil.zerodhaTokenExpiry()));
        }
    }

    @Override
    public String jwtToken() {
        return jwtToken;
    }

    @Override
    public String feedToken() {
        return feedToken;
    }

    @Override
    public String apiKey() {
        return mongoConfigService.getConfig().getAngelOneConfig().getApiKey();
    }

    @Override
    public String clientId() {
        return mongoConfigService.getConfig().getAngelOneConfig().getClientId();
    }

    private void storeTokens(AngelOneLoginResponse.LoginData loginData) {
        this.jwtToken = loginData.getJwtToken();
        this.feedToken = loginData.getFeedToken();
    }
}
