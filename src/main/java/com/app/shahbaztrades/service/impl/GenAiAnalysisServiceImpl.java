package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.analysis.GenAiClient;
import com.app.shahbaztrades.components.yahoo.YahooClient;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.analysis.AIAnalysis;
import com.app.shahbaztrades.repo.redis.GenAiRedisRepo;
import com.app.shahbaztrades.service.GenAiAnalysisService;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.util.DateUtil;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenAiAnalysisServiceImpl implements GenAiAnalysisService {

    private static final String NOT_FOUND = "Analysis Not Found";
    private static final long LOCK_WAIT_SECONDS = 20;
    private static final Duration ANALYSIS_TTL = Duration.ofMinutes(10);

    private final GenAiClient genAiClient;
    private final YahooClient yahooClient;
    private final MongoConfigService mongoConfigService;
    private final GenAiRedisRepo<AIAnalysis> genAiRedisRepo;
    private final JsonMapper jsonMapper;

    /**
     * Generation is slow and billed per call, so concurrent requests for one symbol queue on a
     * distributed lock and all but the first serve the cache the winner wrote.
     */
    @Override
    public AIAnalysis getGenAiAnalysis(String symbol) {
        AIAnalysis value = genAiRedisRepo.get(symbol);
        if (value != null) {
            return value;
        }

        var lock = genAiRedisRepo.getLock(symbol);

        try {
            if (lock.tryLock(LOCK_WAIT_SECONDS, -1, TimeUnit.SECONDS)) {
                try {
                    return generateOrServeCached(symbol);
                } finally {
                    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                log.warn("Could not acquire lock for symbol: {}, server is busy", symbol);
                throw new NotFoundException(NOT_FOUND);
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new NotFoundException(NOT_FOUND);
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error while getting GenAiStockAnalysis data", e);
            throw new NotFoundException(NOT_FOUND);
        }
    }

    private AIAnalysis generateOrServeCached(String symbol) throws Exception {
        // Re-check under the lock: whoever held it before us may already have generated this.
        AIAnalysis value = genAiRedisRepo.get(symbol);
        if (value != null) {
            return value;
        }

        var history = yahooClient.getMonthlyHistoricalData(symbol);
        if (CollectionUtils.isEmpty(history)) {
            throw new NotFoundException(NOT_FOUND);
        }

        var analysis = genAiClient.getGenAiStockAnalysis(symbol, history,
                mongoConfigService.getConfig().getGoogleAuth().getGeminiKey());
        if (StringUtils.isEmpty(analysis)) {
            throw new NotFoundException(NOT_FOUND);
        }

        AIAnalysis res = jsonMapper.readValue(analysis, AIAnalysis.class);
        genAiRedisRepo.set(symbol, res, DateUtil.getDurationUntilMarketOpen(ANALYSIS_TTL));
        return res;
    }
}
