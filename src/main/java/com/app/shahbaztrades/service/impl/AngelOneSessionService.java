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
        return mongoConfigService.getAngelOneJwtToken();
    }

    @Override
    public String feedToken() {
        return mongoConfigService.getAngelOneFeedToken();
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
        mongoConfigService.setAngelOneJwtToken(loginData.getJwtToken());
        mongoConfigService.setAngelOneFeedToken(loginData.getFeedToken());
    }
}
