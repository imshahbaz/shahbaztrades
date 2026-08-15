package com.app.shahbaztrades.model.entity;

import com.app.shahbaztrades.model.enums.Environments;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldNameConstants
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(collection = "server_configs")
public class ServerConfigurations {

    @MongoId
    String id;

    Environments environment;

    @Builder.Default
    List<String> frontendUrls = new ArrayList<>();

    float leverage;

    String jwtSecret;

    String redisUrl;

    GoogleAuthCredentials googleAuth;

    AngelOneConfig angelOneConfig;

    FcmConfig fcmConfig;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class GoogleAuthCredentials {
        String clientId;
        String secret;
        String callbackUrl;
        String encryptionKey;
        String geminiKey;
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
}
