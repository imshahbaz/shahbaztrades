package com.app.shahbaztrades.controller;

import org.springframework.validation.annotation.Validated;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.service.MarginService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collection;

@RestController
@RequestMapping("/api/margin")
@RequiredArgsConstructor
@Validated
public class MarginController {

    private final MarginService marginService;

    @PublicEndpoint
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<Collection<Margin>>> getAllMargins() {
        return ResponseEntity.ok(ApiResponse.ok(marginService.getAllMargins(), "Success"));
    }

    @PublicEndpoint
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<ApiResponse<Margin>> getMargin(@PathVariable @NotBlank String symbol) {
        return ResponseEntity.ok(ApiResponse.ok(marginService.getMargin(symbol.toUpperCase()), "Success"));
    }

    @PublicEndpoint
    @PostMapping("/reload")
    public ResponseEntity<ApiResponse<Void>> reload() {
        marginService.refreshMargins();
        return ResponseEntity.ok().build();
    }

    @PublicEndpoint
    @PostMapping(value = "/json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> syncMtf(@RequestParam("file") MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        marginService.syncMTF(bytes);
        return ResponseEntity.ok(ApiResponse.ok(null, "MTF data synced successfully"));
    }

    @PublicEndpoint
    @PostMapping("/sync-token")
    public ResponseEntity<ApiResponse<Void>> syncAngelOneToken() {
        marginService.syncAngelOneToken();
        return ResponseEntity.ok(ApiResponse.ok(null, "Angel one token synced successfully"));
    }

}
