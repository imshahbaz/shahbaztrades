package com.shahbaz.trades.model.dto;

import com.shahbaz.trades.model.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;

@Data
@Builder
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class UserDto {

    @NotBlank
    String userName;

    @Email
    @NotBlank
    String email;

    @NotBlank
    String password;

    User.Role role;

    public User toEntity(PasswordEncoder passwordEncoder){
        return User.builder()
                .userName(userName)
                .email(email)
                .password(passwordEncoder.encode(password))
                .build();
    }

}
