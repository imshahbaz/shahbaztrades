package com.shahbaz.trades.model.entity;

import com.shahbaz.trades.model.dto.UserDto;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@Builder
@Document(collection = "users")
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class User {

    @MongoId
    String email;

    String userName;

    String password;

    @Builder.Default
    Role role = Role.USER;

    public enum Role {
        ADMIN, USER
    }

    public UserDto toDto(){
        return UserDto.builder()
                .userName(userName)
                .email(email)
                .role(role)
                .build();
    }

}
