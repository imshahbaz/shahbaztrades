package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.AuthRequest;
import com.app.shahbaztrades.model.dto.auth.SignUpResponse;
import com.app.shahbaztrades.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignUpResponse>> signUp(@RequestBody @Valid AuthRequest request) {
        return authService.signUp(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> Logout() {
        return authService.Logout();
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser(@RequestAttribute("user") UserDto userDto) {
        return authService.getMe(userDto);
    }

    @PostMapping("/google/token")
    public ResponseEntity<ApiResponse<String>> validateGoogleToken(@RequestParam @NotBlank String code) {
        return authService.validateGoogleToken(code);
    }

    @GetMapping("/google/callback")
    public ResponseEntity<?> getGoogleCallback(@RequestParam @NotBlank String code, @RequestParam @NotBlank String state) {
        return authService.googleAuthCallback(code, state);
    }

}
