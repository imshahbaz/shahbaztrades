package com.app.shahbaztrades.config;

import com.app.shahbaztrades.service.MongoConfigService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Bootstraps the Firebase SDK once, so the notification service can be handed a ready client
 * instead of standing up the SDK inside its own constructor.
 */
@Configuration
@RequiredArgsConstructor
public class FirebaseConfig {

    private final MongoConfigService mongoConfigService;
    private final JsonMapper jsonMapper;

    @Bean
    public FirebaseMessaging firebaseMessaging() throws IOException {
        var serviceAccount = jsonMapper.writeValueAsBytes(
                mongoConfigService.getConfig().getFcmConfig().getServiceAccount());

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(serviceAccount)))
                .build();

        // FirebaseApp is a process-wide singleton; initialising it twice throws.
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }

        return FirebaseMessaging.getInstance();
    }
}
