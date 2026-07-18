package com.app.shahbaztrades.controller;

import org.springframework.validation.annotation.Validated;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.enums.UserTheme;
import com.app.shahbaztrades.service.FcmService;
import com.app.shahbaztrades.service.UserService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
@Validated
public class UserController {

    private static final String TOKEN = "token";

    private final UserService userService;
    private final FcmService fcmService;

    @PatchMapping("/fcm-token")
    public ResponseEntity<ApiResponse<String>> patchFcmToken(@RequestAttribute("user") UserDto userDto,
                                                             @RequestBody @NotNull @NotEmpty Map<String, String> payload) {
        fcmService.saveToken(userDto.getUserId(), payload.get(TOKEN));
        return ResponseEntity.ok(ApiResponse.ok(payload.get(TOKEN), "FCM token synchronized"));
    }

    @PatchMapping("/username")
    public ResponseEntity<ApiResponse<Void>> patchUsername(@RequestBody UserDto userDto) {
        userService.updateUserName(userDto);
        return ResponseEntity.ok(ApiResponse.ok(null, "Username updated successfully"));
    }

    @PatchMapping("/theme")
    public ResponseEntity<ApiResponse<UserTheme>> patchTheme(@RequestBody UserDto userDto, @RequestAttribute("user") UserDto currentUser) {
        userDto.setUserId(currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(userService.updateUserTheme(userDto), "Theme synchronized"));
    }

    @PublicEndpoint
    @PostMapping("/fcm-token/remove")
    public ResponseEntity<ApiResponse<Void>> removeToken(@RequestBody @NotNull @NotEmpty Map<String, String> payload) {
        fcmService.removeToken(payload.get(TOKEN));
        return ResponseEntity.ok(ApiResponse.ok(null, "FCM Token removed"));
    }

}
