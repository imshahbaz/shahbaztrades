package com.app.shahbaztrades.components.auth;

import com.app.shahbaztrades.model.dto.auth.GoogleUser;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.util.HelperUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleAuthUtils {

    private final MongoConfigService mongoConfigService;

    public GoogleUser validateIdToken(String idTokenString) {
        if (idTokenString == null || idTokenString.isEmpty()) {
            throw new RuntimeException("Empty ID token");
        }

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(mongoConfigService.getConfig().getGoogleAuth().getClientId()))
                .build();

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);

            if (idToken == null) {
                return null;
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            return GoogleUser.builder()
                    .email(payload.getEmail())
                    .verifiedEmail(payload.getEmailVerified())
                    .name((String) payload.get("name"))
                    .picture((String) payload.get("picture"))
                    .familyName((String) payload.get("family_name"))
                    .givenName((String) payload.get("given_name"))
                    .build();

        } catch (Exception e) {
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
            throw new RuntimeException("Failed to exchange code for access token");
        }
        return (String) response.get("access_token");
    }

    private GoogleUser fetchGoogleUserInfo(String accessToken) {
        String url = "https://www.googleapis.com/oauth2/v2/userinfo?access_token=" + accessToken;
        return HelperUtil.REST_TEMPLATE.getForObject(url, GoogleUser.class);
    }

}