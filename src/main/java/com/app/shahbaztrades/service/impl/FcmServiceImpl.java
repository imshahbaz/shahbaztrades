package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.model.entity.FcmToken;
import com.app.shahbaztrades.repo.FcmTokenRepository;
import com.app.shahbaztrades.service.FcmService;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.util.HelperUtil;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FcmServiceImpl implements FcmService {

    private static final String DEFAULT = "default";

    private final FirebaseMessaging messaging;
    private final FcmTokenRepository fcmTokenRepository;

    public FcmServiceImpl(MongoConfigService mongoConfigService, FcmTokenRepository fcmTokenRepository) throws IOException {
        this.fcmTokenRepository = fcmTokenRepository;
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
    public void saveToken(long userId, String token) {
        if (StringUtils.isEmpty(token)) {
            throw new BadRequestException("Token is empty!");
        }

        fcmTokenRepository.deleteByTokenAndUserIdNot(token, userId);

        FcmToken record = fcmTokenRepository.findByToken(token)
                .orElseGet(() -> FcmToken.createEntity(userId, token));

        if (StringUtils.isNotEmpty(record.getId())) {
            record.setToken(token);
            record.setUserId(userId);
        }
        fcmTokenRepository.save(record);
    }

    @Override
    public void removeToken(String token) {
        if (StringUtils.isEmpty(token)) return;
        fcmTokenRepository.deleteByToken(token);
    }

    @Override
    public void sendNotification(long userId, String title, String body, Map<String, String> data) {
        List<FcmToken> tokens = fcmTokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            log.info("No FCM tokens registered for user {}", userId);
            return;
        }

        Map<String, String> payload = data == null ? new HashMap<>() : new HashMap<>(data);
        payload.put("title", title);
        payload.put("body", body);
        payload.put("tag", String.valueOf(System.currentTimeMillis() / 1000));

        List<String> tokenStrings = tokens.stream().map(FcmToken::getToken).toList();

        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokenStrings)
                .putAllData(payload)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(DEFAULT)
                                .setSound(DEFAULT)
                                .setTag(payload.get("tag"))
                                .build())
                        .build())
                .setWebpushConfig(WebpushConfig.builder()
                        .putHeader("Urgency", "high")
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setSound(DEFAULT)
                                .build())
                        .build())
                .build();

        try {
            BatchResponse response = messaging.sendEachForMulticast(message);
            pruneFailedTokens(tokenStrings, response);
            log.info("Sent FCM to user {}: {} success, {} failure",
                    userId, response.getSuccessCount(), response.getFailureCount());
        } catch (FirebaseMessagingException e) {
            log.error("Error sending multicast FCM message for user {}: ", userId, e);
        }
    }

    private void pruneFailedTokens(List<String> tokenStrings, BatchResponse response) {
        List<SendResponse> results = response.getResponses();
        for (int i = 0; i < results.size(); i++) {
            SendResponse result = results.get(i);
            if (result.isSuccessful()) continue;

            FirebaseMessagingException e = result.getException();
            MessagingErrorCode code = e == null ? null : e.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                removeToken(tokenStrings.get(i));
                log.info("Pruned dead FCM token: {}", tokenStrings.get(i));
            } else {
                log.warn("Failed to send to token {}", tokenStrings.get(i), e);
            }
        }
    }

}