package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.AdminOnly;
import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.entity.MongoEnvConfig;
import com.app.shahbaztrades.service.MongoConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/config")
public class ConfigController {

    private final MongoConfigService mongoConfigService;

    @PublicEndpoint
    @GetMapping("/client/active")
    public ResponseEntity<ApiResponse<MongoEnvConfig>> getClientConfig() {
        return ResponseEntity.ok(ApiResponse.ok(mongoConfigService.getClientConfig(), "Client config fetch success"));
    }

    @AdminOnly
    @PostMapping("/reload")
    public ResponseEntity<ApiResponse<Void>> reloadMongoConfig() {
        mongoConfigService.refreshConfig();
        return ResponseEntity.ok(ApiResponse.ok(null, "Mongo Configs Loaded Successfully"));
    }

    @AdminOnly
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<MongoEnvConfig>> getActiveConfig() {
        return ResponseEntity.ok(ApiResponse.ok(mongoConfigService.getConfig(), "Success"));
    }

}
