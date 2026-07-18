package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.sessionmanager.ZerodhaLoginResponseDTO;
import com.app.shahbaztrades.service.SessionManagerService;
import com.app.shahbaztrades.service.ZerodhaService;
import com.app.shahbaztrades.util.Constants;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/session-manager")
public class SessionManagerController {

    private final SessionManagerService sessionManagerService;
    private final ZerodhaService zerodhaService;

    @PublicEndpoint
    @PostMapping("/init-zerodha-session")
    public ApiResponse<ResponseEntity<Void>> initZerodhaSession() throws ExecutionException, InterruptedException {
        sessionManagerService.initiateZerodhaLogin();
        return ApiResponse.ok(null, "Initiated zerodha login successfully");
    }

    @PostMapping("/zerodha-auto-connect")
    public ResponseEntity<ApiResponse<Boolean>> autoConnectZerodhaSession(@RequestAttribute("user") UserDto userDto) {
        return ResponseEntity.ok(ApiResponse.ok(sessionManagerService.autoConnectZerodhaSession(userDto), "Token generation initiated successfully"));
    }

    @PublicEndpoint
    @PostMapping("/zerodha-callback")
    public ResponseEntity<ApiResponse<Void>> sessionManagerCallback(@RequestBody ZerodhaLoginResponseDTO request, @RequestHeader @NotBlank String source) {
        Constants.validateSessionCallback(source);
        zerodhaService.sessionManagerCallback(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Accepted request"));
    }

}
