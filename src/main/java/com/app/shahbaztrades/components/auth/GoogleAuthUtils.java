package com.app.shahbaztrades.components.auth;

import com.app.shahbaztrades.model.dto.auth.GoogleUser;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.util.HelperUtil;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.auth.oauth2.TokenVerifier;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class GoogleAuthUtils {

    private final MongoConfigService mongoConfigService;
    private final TokenVerifier verifier;
    private final JsonMapper jsonMapper;

    public GoogleAuthUtils(MongoConfigService mongoConfigService, JsonMapper jsonMapper) {
        this.mongoConfigService = mongoConfigService;
        this.jsonMapper = jsonMapper;
        this.verifier = TokenVerifier.newBuilder()
                .setAudience(mongoConfigService.getConfig().getGoogleAuth().getClientId())
                .build();
    }

    public GoogleUser validateIdToken(String idTokenString) {
        if (StringUtils.isEmpty(idTokenString)) {
            throw new IllegalArgumentException("Empty ID token");
        }

        try {
            var result = verifier.verify(idTokenString);
            if (result == null) {
                return null;
            }
            return jsonMapper.convertValue(result.getPayload(), GoogleUser.class);
        } catch (TokenVerifier.VerificationException e) {
            log.warn("Google ID token verification failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error during Google ID token verification", e);
            return null;
        }
    }

    public GoogleUser googleCallbackProcessing(String code, String uuid) {
        try {
            String accessToken = exchangeCodeForToken(code);
            return fetchGoogleUserInfo(accessToken);
        } catch (Exception e) {
            log.error("Google background processing failed for UUID: {}", uuid, e);
        }
        return null;
    }

    private String exchangeCodeForToken(String code) {
        var config = mongoConfigService.getConfig().getGoogleAuth();
        String url = "https://oauth2.googleapis.com/token";

        Map<String, String> params = Map.of(
                "code", code,
                "client_id", config.getClientId(),
                "client_secret", config.getSecret(),
                "redirect_uri", config.getCallbackUrl(),
                "grant_type", "authorization_code"
        );

        Map<String, Object> response = HelperUtil.REST_TEMPLATE.postForObject(url, params, Map.class);
        if (response == null || !response.containsKey("access_token")) {
            throw new IllegalStateException("Failed to exchange code for access token");
        }
        return (String) response.get("access_token");
    }

    private GoogleUser fetchGoogleUserInfo(String accessToken) {
        String url = "https://www.googleapis.com/oauth2/v2/userinfo?access_token=" + accessToken;
        return HelperUtil.REST_TEMPLATE.getForObject(url, GoogleUser.class);
    }

}