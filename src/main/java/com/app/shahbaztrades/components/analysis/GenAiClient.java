package com.app.shahbaztrades.components.analysis;

import com.app.shahbaztrades.model.dto.nse.NSEHistoricalData;
import com.google.genai.Client;
import com.google.genai.types.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GenAiClient {

    private static final String MODEL_FLASH_25 = "gemini-2.5-flash";
    private static final String SYSTEM_INSTRUCTION = """
            You are a professional NSE technical analyst and quant trader.
            
            TASK:
            Using ONLY the provided daily OHLC data (1 month), predict TOMORROW'S PRICE RANGE.
            
            STRICT RULES:
            - Do NOT use news, fundamentals, sentiment, or assumptions.
            - Do NOT guess.
            - Base analysis strictly on price action and volatility.
            - Assume tomorrow is a normal trading session (no gap unless justified by data).
            
            ANALYSIS REQUIREMENTS:
            1. Identify the current short-term trend (Bullish / Bearish / Sideways).
            2. Detect key support and resistance levels using recent highs/lows.
            3. Measure volatility using:
               - Average true range approximation
               - Recent candle ranges
            4. Identify momentum:
               - Higher highs / higher lows OR lower highs / lower lows
            5. Determine likely price behavior for NEXT trading day only.
            
            PREDICTION OUTPUT:
            - Provide a realistic TOMORROW LOW and TOMORROW HIGH range.
            - The range must be achievable within normal NSE intraday volatility.
            - Do NOT give extreme or unlikely levels.
            
            OUTPUT FORMAT:
            Return ONLY valid, minified JSON.
            No explanation outside JSON.
            No markdown.
            No extra text.""";
    private final Map<String, Client> clientCache = new ConcurrentHashMap<>();

    private Schema createResponseSchema() {
        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "action", Schema.builder()
                                .type(Type.Known.STRING)
                                .enum_(List.of("BUY", "SELL", "HOLD"))
                                .build(),
                        "trend", Schema.builder()
                                .type(Type.Known.STRING)
                                .enum_(List.of("BULLISH", "BEARISH", "SIDEWAYS"))
                                .build(),
                        "tomorrow_low", Schema.builder().type(Type.Known.NUMBER).build(),
                        "tomorrow_high", Schema.builder().type(Type.Known.NUMBER).build(),
                        "confidence", Schema.builder().type(Type.Known.INTEGER).build(),
                        "reasoning", Schema.builder().type(Type.Known.STRING).build()
                ))
                .required(List.of("action", "trend", "tomorrow_low", "tomorrow_high", "confidence", "reasoning"))
                .build();
    }

    public String getGenAiStockAnalysis(String symbol, List<NSEHistoricalData> data, String apiKey) {
        log.info("Invoking Gemini Quant Analysis engine for target ticker via SDK v1.57.0: {}", symbol);

        // 1. Convert historical candles list to custom delimited rows
        String dataRows = data.stream()
                .map(d -> String.format("%s|%.2f|%.2f|%.2f|%.2f",
                        d.getTimestamp(), d.getOpen(), d.getHigh(), d.getLow(), d.getClose()))
                .collect(Collectors.joining("\n"));

        // 2. Compose the structural operational prompt block
        String finalPrompt = String.format("""
                        Stock: %s
                        Data format: Date|Open|High|Low|Close (Daily candles)
                        
                        OHLC DATA (Latest → Oldest):
                        %s
                        
                        Predict tomorrow's intraday range.""",
                symbol, dataRows);

        // 3. Initialize or retrieve the SDK Client wrapper using specific v1.57.0 components
        Client client = clientCache.computeIfAbsent(apiKey, key -> Client.builder()
                .apiKey(key)
                .build());

        // 4. Set model configurations matching parameters precisely
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.builder().role("user").parts(List.of(Part.builder().text(SYSTEM_INSTRUCTION).build())).build())
                .temperature(0.0f)
                .topP(0.1f)
                .seed(21)
                .responseMimeType("application/json")
                .responseSchema(createResponseSchema())
                .build();

        try {
            // 5. Execute text completion sequence through models instance
            GenerateContentResponse response = client.models.generateContent(
                    MODEL_FLASH_25,
                    finalPrompt,
                    config
            );

            if (response == null || response.text() == null) {
                throw new IllegalStateException("Empty response matrix returned from target execution model context.");
            }

            return response.text();

        } catch (Exception e) {
            log.error("Fatal exception during content generation flow mapping", e);
        }

        return null;
    }

}