package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
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
public class UserController {

    private final UserService userService;

    @PatchMapping("/fcm-token")
    public ResponseEntity<ApiResponse<String>> patchFcmToken(@RequestAttribute("user") UserDto userDto,
                                                             @RequestBody @NotNull @NotEmpty Map<String, String> payload) {
        return userService.patchFcmToken(userDto, payload);
    }
}
