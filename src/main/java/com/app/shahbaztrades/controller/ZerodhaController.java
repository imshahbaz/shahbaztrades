package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.zerodha.BrokerLoginDto;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.service.ZerodhaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/zerodha")
public class ZerodhaController {

    private final ZerodhaService zerodhaService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(@RequestBody @Valid BrokerLoginDto request) {
        zerodhaService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Flow invocation success"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<String>> getAuth(@RequestAttribute("user") UserDto userDto) {
        return ResponseEntity.ok(zerodhaService.getAuth(userDto));
    }

    @PostMapping("/config")
    public ResponseEntity<ApiResponse<Long>> setConfig(@RequestBody User.ZerodhaConfig config, @RequestAttribute("user") UserDto userDto) {
        return ResponseEntity.ok(ApiResponse.ok(zerodhaService.setConfig(config, userDto), "Zerodha configuration updated successfully"));
    }

}
