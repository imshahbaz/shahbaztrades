package com.shahbaz.trades.service;

import com.shahbaz.trades.model.dto.UserDto;
import com.shahbaz.trades.model.entity.User;

public interface UserService {

    UserDto createUser(UserDto request);

    UserDto updateUser(UserDto request);

    UserDto updateUserTheme(String email, User.Theme theme);

    UserDto updateUsername(String email, String username);

    UserDto getUser(String email);

    void deleteUser(String email);

}
