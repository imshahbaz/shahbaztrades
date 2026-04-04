package com.app.shahbaztrades.model.entity;

import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.enums.UserRole;
import com.app.shahbaztrades.model.enums.UserTheme;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
@Document(collection = "users")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User {

    @MongoId
    long userId;
    String email;
    String username;
    String password;
    UserRole role;
    UserTheme theme;
    long mobile;
    String name;
    String profile;
    ZerodhaConfig zerodhaConfig;
    MstockConfig mstockConfig;
    String fcmToken;

    public UserDto toDto() {
        return UserDto.builder()
                .userId(this.userId)
                .email(this.email)
                .username(this.username)
                .role(this.role)
                .theme(this.theme)
                .mobile(this.mobile)
                .name(this.name)
                .profile(this.profile)
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ZerodhaConfig {

        String apiKey;
        String apiSecret;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class MstockConfig {

        String apiKey;
        String username;
        String password;
    }

}