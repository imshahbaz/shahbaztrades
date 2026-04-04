package com.app.shahbaztrades.model.dto.auth;

import com.app.shahbaztrades.model.dto.UserDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthRequest {

    @Email
    @NotNull
    String email;

    @NotNull
    String password;

    String confirmPassword;

    public UserDto toUserDto() {
        return UserDto.builder()
                .email(email)
                .password(password)
                .build();
    }

}
