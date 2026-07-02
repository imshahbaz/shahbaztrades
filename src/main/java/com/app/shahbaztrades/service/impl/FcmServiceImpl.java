package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.service.FcmService;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.util.HelperUtil;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class FcmServiceImpl implements FcmService {

    private static final String DEFAULT_CHANNEL_ID = "default";

    private final FirebaseMessaging messaging;

    public FcmServiceImpl(MongoConfigService mongoConfigService) throws IOException {
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(
                        new ByteArrayInputStream(HelperUtil.GSON.toJson(mongoConfigService.getConfig().getFcmConfig().getServiceAccount())
                                .getBytes(StandardCharsets.UTF_8))))
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }

        this.messaging = FirebaseMessaging.getInstance();
    }

    @Override
    public void sendNotification(String token, String title, String body, Map<String, String> data) {
        Map<String, String> payload = data == null ? new HashMap<>() : new HashMap<>(data);
        payload.put("title", title);
        payload.put("body", body);
        payload.put("tag", String.valueOf(System.currentTimeMillis() / 1000));

        Message message = Message.builder()
                .setToken(token)
                .putAllData(payload)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(DEFAULT_CHANNEL_ID)
                                .setSound("default")
                                .setTag(payload.get("tag"))
                                .build())
                        .build())
                .setWebpushConfig(WebpushConfig.builder()
                        .putHeader("Urgency", "high")
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setSound("default")
                                .build())
                        .build())
                .build();

        try {
            String response = messaging.send(message);
            log.info("Successfully sent FCM message: {}", response);
        } catch (FirebaseMessagingException e) {
            log.error("Error sending FCM message to token {}: ", token, e);
        }
    }

}