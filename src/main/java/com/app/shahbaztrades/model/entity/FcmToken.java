package com.app.shahbaztrades.model.entity;

import com.app.shahbaztrades.util.DateUtil;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;
import java.time.ZonedDateTime;

@Data
@Builder
@Document(collection = "fcm_tokens")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FcmToken {

    @MongoId
    String id;

    long userId;

    private String token;

    @Builder.Default
    private Instant createdAt = ZonedDateTime.now(DateUtil.IST_ZONE).toInstant();

    private Instant lastSeenAt;

    public static FcmToken createEntity(long userId, String token) {
        return FcmToken.builder()
                .userId(userId)
                .token(token)
                .lastSeenAt(ZonedDateTime.now(DateUtil.IST_ZONE).toInstant())
                .build();
    }
}