package com.app.shahbaztrades.components.logs;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AxiomHttpAppender extends AppenderBase<ILoggingEvent> {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Setter
    private String token;
    @Setter
    private String dataset;

    @Override
    protected void append(ILoggingEvent event) {

        try {

            LinkedHashMap<String, Object> eventPayload = new LinkedHashMap<>();

            eventPayload.put("level", event.getLevel().toString());
            eventPayload.put("logger", event.getLoggerName());
            eventPayload.put("thread", event.getThreadName());
            eventPayload.put("message", event.getFormattedMessage());

            if (event.getThrowableProxy() != null) {
                eventPayload.put("exception",
                        event.getThrowableProxy().getClassName());

                eventPayload.put("stacktrace",
                        ThrowableProxyUtil.asString(event.getThrowableProxy()));
            }

            List<Map<String, Object>> payload = List.of(eventPayload);

            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "https://api.axiom.co/v1/datasets/" +
                                    dataset +
                                    "/ingest"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            httpClient.sendAsync(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );

        } catch (Exception e) {
            addError("Failed to send log to Axiom", e);
        }
    }
}