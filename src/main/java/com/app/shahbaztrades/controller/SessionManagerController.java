package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.service.SessionManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutionException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/session-manager")
public class SessionManagerController {

    private final SessionManagerService sessionManagerService;

    @PublicEndpoint
    @PostMapping("/init-zerodha-session")
    public ApiResponse<ResponseEntity<Void>> initZerodhaSession() throws ExecutionException, InterruptedException {
        sessionManagerService.initiateZerodhaLogin();
        return ApiResponse.ok(null, "Initiated zerodha login successfully");
    }

    @PostMapping("/zerodha-auto-connect")
    public ResponseEntity<ApiResponse<Boolean>> autoConnectZerodhaSession(@RequestAttribute("user") UserDto userDto) {
        return sessionManagerService.autoConnectZerodhaSession(userDto);
    }

}
