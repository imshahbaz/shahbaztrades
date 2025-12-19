package com.shahbaz.trades.model.entity;

import com.shahbaz.trades.model.dto.UserDto;
import jakarta.annotation.Nonnull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Data
@Builder
@Document(collection = "users")
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class User implements UserDetails {

    @MongoId
    String email;

    String username;

    String password;

    @Builder.Default
    Role role = Role.USER;

    @Nonnull
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_"+role.name()));
    }

    @Nonnull
    @Override
    public String getUsername() {
        return getEmail();
    }

    public enum Role {
        ADMIN, USER
    }

    public UserDto toDto(){
        return UserDto.builder()
                .username(username)
                .email(email)
                .role(role)
                .build();
    }

}
