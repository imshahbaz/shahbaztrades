package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.kronos.BulkPredictionRequestDto;
import com.app.shahbaztrades.model.dto.kronos.KronosPredictionResponse;
import com.app.shahbaztrades.model.entity.KronosPredictions;
import com.app.shahbaztrades.repo.KronosPredictionsRepo;
import com.app.shahbaztrades.service.KronosPredictionService;
import com.app.shahbaztrades.service.NseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KronosPredictionServiceImpl implements KronosPredictionService {

    private final KronosPredictionsRepo kronosPredictionsRepo;
    private final NseService nseService;

    @Override
    @Async("taskExecutor")
    public void savePredictions(BulkPredictionRequestDto request) {
        if (CollectionUtils.isEmpty(request.getPredictions())) {
            log.info("No predictions found in request");
            return;
        }

        Map<String, KronosPredictions> predictions = HashMap.newHashMap(200);
        request.getPredictions().forEach(prediction -> {
            var entity = predictions.computeIfAbsent(prediction.getSymbol(), (_) -> prediction.mapKronosPredictions());
            entity.getPredictedCandles().add(prediction.mapPredictedCandle());
        });

        if (CollectionUtils.isEmpty(predictions)) {
            return;
        }

        log.info("Saving predictions for kronos");
        kronosPredictionsRepo.saveAll(predictions.values());
    }

    @Override
    public KronosPredictionResponse getPredictions(String symbol) {
        var prediction = kronosPredictionsRepo.findBySymbol(symbol, Sort.by(Sort.Direction.DESC, KronosPredictions.Fields.createdAt));

        if (prediction.isPresent()) {
            return KronosPredictionResponse.fromKronosPrediction(prediction.get(), nseService.getHistoricalData(symbol));
        }

        throw new NotFoundException("Prediction not found");
    }
}
