package com.shahbaz.trades.service;

import com.shahbaz.trades.model.dto.UserDto;

public interface UserService {

    UserDto createUser(UserDto request);

    UserDto updateUser(UserDto request);

    UserDto getUser(String email);

    void deleteUser(String email);
}
