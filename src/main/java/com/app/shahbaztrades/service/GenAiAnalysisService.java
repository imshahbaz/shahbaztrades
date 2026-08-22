package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.analysis.AIAnalysis;

/** LLM-written commentary on a symbol, generated on demand and cached. */
public interface GenAiAnalysisService {

    AIAnalysis getGenAiAnalysis(String symbol);
}
