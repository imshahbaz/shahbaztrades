package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.kronos.BulkPredictionRequestDto;
import com.app.shahbaztrades.model.dto.kronos.KronosPredictionResponse;

public interface KronosPredictionService {

    void savePredictions(BulkPredictionRequestDto request);

    KronosPredictionResponse getPredictions(String symbol);
}
