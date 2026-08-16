package com.app.shahbaztrades.controller.admin;

import com.app.shahbaztrades.config.security.AdminOnly;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.entity.ServerConfigurations;
import com.app.shahbaztrades.model.enums.ConfigurationType;
import com.app.shahbaztrades.service.MongoConfigService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/config")
public class ConfigControllerAdmin {

    private final MongoConfigService mongoConfigService;

    @AdminOnly
    @PostMapping("/reload")
    public ResponseEntity<ApiResponse<Void>> reloadMongoConfig() {
        mongoConfigService.refreshConfig();
        return ResponseEntity.ok(ApiResponse.ok(null, "Mongo Configs Loaded Successfully"));
    }

    @AdminOnly
    @PostMapping("/client/reload")
    public ResponseEntity<ApiResponse<Void>> reloadClientMongoConfig() {
        mongoConfigService.refreshClientConfig();
        return ResponseEntity.ok(ApiResponse.ok(null, "Mongo Client Configs Loaded Successfully"));
    }

    @AdminOnly
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<ServerConfigurations>> getActiveConfig() {
        return ResponseEntity.ok(ApiResponse.ok(mongoConfigService.getConfig(), "Success"));
    }

    @AdminOnly
    @PutMapping("/update/{id}")
    public ResponseEntity<ApiResponse<Void>> updateConfig(@PathVariable @NotBlank String id, @RequestParam @NotNull ConfigurationType configurationType,
                                                          @RequestBody @NotEmpty Map<String, Object> request) {
        mongoConfigService.updatePartialConfig(id, configurationType, request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Success"));
    }

}
