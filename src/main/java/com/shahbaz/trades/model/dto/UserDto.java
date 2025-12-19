package com.shahbaz.trades.model.dto;

import com.shahbaz.trades.model.entity.User;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.bcrypt.BCrypt;

@Data
@Builder
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class UserDto {

    String username;

    @Email
    @NotBlank
    String email;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character")
    String password;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character")
    String confirmPassword;

    User.Role role;

    User.Theme theme;

    @AssertTrue(message = "Passwords do not match")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }

    public User toEntity() {
        return User.builder()
                .username(email.split("@")[0].toLowerCase())
                .email(email)
                .password(BCrypt.hashpw(password, BCrypt.gensalt()))
                .build();
    }

}
