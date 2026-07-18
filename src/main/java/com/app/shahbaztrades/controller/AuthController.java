package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.AuthCallbackResponse;
import com.app.shahbaztrades.model.dto.auth.AuthCookieResponse;
import com.app.shahbaztrades.model.dto.auth.AuthRequest;
import com.app.shahbaztrades.model.dto.auth.SignUpResponse;
import com.app.shahbaztrades.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PublicEndpoint
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignUpResponse>> signUp(@RequestBody @Valid AuthRequest request) {
        var response = authService.signUp(request);
        return ResponseEntity.ok(ApiResponse.ok(response, response.getMessage()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authService.logout())
                .body(ApiResponse.ok(null, "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser(@RequestAttribute("user") UserDto userDto) {
        return ResponseEntity.ok(ApiResponse.ok(authService.getMe(userDto), "User details fetched"));
    }

    @PublicEndpoint
    @PostMapping("/google/token")
    public ResponseEntity<ApiResponse<String>> validateGoogleToken(@RequestParam @NotBlank String code, @RequestHeader(required = false) boolean nativeFlow) {
        AuthCookieResponse<String> result = authService.validateGoogleToken(code, nativeFlow);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (result.cookie() != null) {
            builder.header(HttpHeaders.SET_COOKIE, result.cookie());
        }
        return builder.body(ApiResponse.ok(result.data(), result.message()));
    }

    @PublicEndpoint
    @GetMapping("/google/callback")
    public ResponseEntity<?> getGoogleCallback(@RequestParam @NotBlank String code, @RequestParam @NotBlank String state) {
        AuthCallbackResponse result = authService.googleAuthCallback(code, state);
        if (result.isRedirect()) {
            return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                    .header(HttpHeaders.LOCATION, result.redirectUrl())
                    .build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.cookie())
                .body(ApiResponse.ok(result.user(), result.message()));
    }

}
