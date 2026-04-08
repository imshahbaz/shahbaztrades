package com.app.shahbaztrades.components.angelone;

import com.app.shahbaztrades.model.dto.angelone.MinimalInstrument;
import com.app.shahbaztrades.model.entity.Margin;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AngelOneClient {

    private static final String url = "https://margincalculator.angelbroking.com/OpenAPI_File/files/OpenAPIScripMaster.json";
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AngelOneClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder()
                                .connectTimeout(Duration.ofMinutes(5))
                                .build()
                ))
                .build();
    }

    public List<Margin> getTokens(Map<String, Margin> cachedMargin) {
        List<Margin> margins = new ArrayList<>();
        restClient.get()
                .uri(url)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isError()) {
                        log.error("Failed to fetch Scrip Master. Status: {}", response.getStatusCode());
                        return null;
                    }

                    try (InputStream is = response.getBody();
                         JsonParser parser = objectMapper.getFactory().createParser(is)) {

                        if (parser.nextToken() != JsonToken.START_ARRAY) return null;

                        while (parser.nextToken() == JsonToken.START_OBJECT) {
                            MinimalInstrument inst = objectMapper.readValue(parser, MinimalInstrument.class);
                            if ("NSE".equals(inst.exchSeg()) && inst.symbol().endsWith("-EQ")) {
                                if (cachedMargin.containsKey(inst.name())) {
                                    Margin margin = cachedMargin.get(inst.name());
                                    margin.setToken(inst.token());
                                    margins.add(margin);
                                }
                            }
                        }
                    } catch (IOException e) {
                        log.error("Error while streaming Scrip Master JSON", e);
                    }
                    return null;
                });

        return margins;
    }
}
