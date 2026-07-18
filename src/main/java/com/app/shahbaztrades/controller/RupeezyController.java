package com.app.shahbaztrades.controller;

import org.springframework.validation.annotation.Validated;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.zerodha.BrokerLoginDto;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.service.RupeezyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rupeezy")
@Validated
public class RupeezyController {

    private final RupeezyService rupeezyService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(@RequestBody @Valid BrokerLoginDto request) {
        return rupeezyService.login(request);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<String>> getAuth(@RequestAttribute("user") UserDto userDto) {
        return rupeezyService.getAuth(userDto);
    }

    @PostMapping("/config")
    public ResponseEntity<ApiResponse<Long>> setConfig(@RequestBody User.RupeezyConfig config, @RequestAttribute("user") UserDto userDto) {
        return rupeezyService.setConfig(config, userDto);
    }
}
