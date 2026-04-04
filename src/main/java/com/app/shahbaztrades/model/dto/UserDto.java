package com.app.shahbaztrades.model.dto;

import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.model.enums.UserRole;
import com.app.shahbaztrades.model.enums.UserTheme;
import com.app.shahbaztrades.util.HelperUtil;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserDto {

    long userId;

    String email;

    String username;

    String password;

    UserRole role;

    UserTheme theme;

    long mobile;

    String name;

    String profile;

    public User toEntity() {

        String generatedUsername = "";
        if (this.email != null && !this.email.isEmpty()) {
            generatedUsername = this.email.split("@")[0].toLowerCase();
        } else if (this.name != null && !this.name.isEmpty()) {
            int randomNum = HelperUtil.RANDOM.nextInt(10) + 1;
            generatedUsername = this.name.split(" ")[0].toLowerCase() + randomNum;
        }

        return User.builder()
                .userId(this.userId)
                .username(generatedUsername)
                .email(this.email)
                .password(HelperUtil.ENCODER.encode(this.password))
                .role(UserRole.USER)
                .theme(UserTheme.DARK)
                .mobile(this.mobile)
                .name(this.name)
                .profile(this.profile)
                .build();
    }

}