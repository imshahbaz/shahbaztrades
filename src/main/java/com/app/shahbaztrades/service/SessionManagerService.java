package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.ExecutionException;

public interface SessionManagerService {

    void initiateZerodhaLogin() throws ExecutionException, InterruptedException;

    ResponseEntity<ApiResponse<Boolean>> autoConnectZerodhaSession(UserDto userDto);
}
