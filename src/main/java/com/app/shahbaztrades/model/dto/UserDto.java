package com.app.shahbaztrades.model.dto;

import com.app.shahbaztrades.util.AuthUtil;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.model.enums.UserRole;
import com.app.shahbaztrades.model.enums.UserTheme;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    long userId;

    String email;

    String username;

    String password;

    UserRole role;

    UserTheme theme;

    Long mobile;

    String name;

    String profile;

    public User toEntity() {

        String generatedUsername = "";
        if (this.email != null && !this.email.isEmpty()) {
            generatedUsername = this.email.split("@")[0].toLowerCase();
        } else if (this.name != null && !this.name.isEmpty()) {
            int randomNum = AuthUtil.RANDOM.nextInt(10) + 1;
            generatedUsername = this.name.split(" ")[0].toLowerCase() + randomNum;
        }

        return User.builder()
                .userId(this.userId)
                .username(generatedUsername)
                .email(this.email)
                .password(AuthUtil.ENCODER.encode(this.password))
                .role(UserRole.USER)
                .theme(UserTheme.DARK)
                .mobile(this.mobile)
                .name(this.name)
                .profile(this.profile)
                .build();
    }

}