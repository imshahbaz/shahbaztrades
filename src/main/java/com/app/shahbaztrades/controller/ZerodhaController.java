package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.zerodha.ZerodhaLoginDto;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.service.ZerodhaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/zerodha")
public class ZerodhaController {

    private final ZerodhaService zerodhaService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(@RequestBody @Valid ZerodhaLoginDto request) {
        return zerodhaService.login(request);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<String>> getAuth(@RequestAttribute("user") UserDto userDto) {
        return zerodhaService.getAuth(userDto);
    }

    @PostMapping("/config")
    public ResponseEntity<ApiResponse<Long>> setConfig(@RequestBody User.ZerodhaConfig config, @RequestAttribute("user") UserDto userDto) {
        return zerodhaService.setConfig(config, userDto);
    }

}
