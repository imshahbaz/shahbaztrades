package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.kronos.BulkPredictionRequestDto;
import com.app.shahbaztrades.model.dto.kronos.KronosPredictionResponse;
import com.app.shahbaztrades.service.KronosPredictionService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/kronos")
public class KronosPredictionController {

    private final KronosPredictionService kronosPredictionService;

    @PublicEndpoint
    @PostMapping("/predictions")
    public ResponseEntity<ApiResponse<Void>> savePredictions(@RequestBody BulkPredictionRequestDto request) {
        kronosPredictionService.savePredictions(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Predictions saved successfully"));
    }

    @PublicEndpoint
    @GetMapping("/predictions")
    public ResponseEntity<ApiResponse<KronosPredictionResponse>> getPredictions(@RequestParam @NotBlank String symbol) {
        return ResponseEntity.ok(ApiResponse.ok(kronosPredictionService.getPredictions(symbol), "Predictions fetched!"));
    }
}
