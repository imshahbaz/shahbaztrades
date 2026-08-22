package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.AdminOnly;
import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.sessionmanager.ZerodhaLoginResponseDTO;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.service.SessionManagerService;
import com.app.shahbaztrades.components.broker.BrokerAuthServiceFactory;
import com.app.shahbaztrades.service.ZerodhaAutoLoginService;
import com.app.shahbaztrades.util.Constants;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private final ZerodhaAutoLoginService zerodhaAutoLoginService;
    private final BrokerAuthServiceFactory brokerAuthServiceFactory;

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
        zerodhaAutoLoginService.sessionManagerCallback(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Accepted request"));
    }

    @AdminOnly
    @PostMapping("/broker/revoke-auth")
    public ResponseEntity<ApiResponse<Void>> revokeBrokerAuth(@RequestParam @Min(1) long userId, @RequestParam @NotNull BrokerType brokerType) {
        brokerAuthServiceFactory.forBroker(brokerType).revokeAuth(userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Auth revoke request submitted"));
    }

}
