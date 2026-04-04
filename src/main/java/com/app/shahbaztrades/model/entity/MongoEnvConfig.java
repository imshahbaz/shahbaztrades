package com.app.shahbaztrades.model.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(collection = "configs")
public class MongoEnvConfig {

    @MongoId
    String id;
    List<String> frontendUrls;
    String brevoEmail;
    String brevoApiKey;
    String apiKey;
    float leverage;
    boolean debugMode;
    boolean rateLimiter;
    String jwtSecret;
    String redisUrl;

    GoogleAuthCredentials googleAuth;
    AngelOneConfig angelOneConfig;
    FcmConfig fcmConfig;
    Auth auth;
    Components components;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class GoogleAuthCredentials {
        String clientId;
        String secret;
        String callbackUrl;
        String encryptionKey;
        String geminiApiKey;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class AngelOneConfig {
        String apiKey;
        String clientId;
        String password;
        String seed;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class FcmConfig {
        Map<String, Object> serviceAccount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Auth {
        private boolean google;
        private boolean email;
        private boolean trueCaller;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Components {
        private boolean heatMap;
    }

}