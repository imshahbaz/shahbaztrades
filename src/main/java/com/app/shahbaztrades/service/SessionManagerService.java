package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.UserDto;

import java.util.concurrent.ExecutionException;

public interface SessionManagerService {

    void initiateZerodhaLogin() throws ExecutionException, InterruptedException;

    boolean autoConnectZerodhaSession(UserDto userDto);
}
