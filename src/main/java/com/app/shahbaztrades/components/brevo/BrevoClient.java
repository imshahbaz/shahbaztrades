package com.app.shahbaztrades.components.brevo;

import com.app.shahbaztrades.model.dto.brevo.BrevoEmailRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BrevoClient {

    private final RestClient restClient;

    public BrevoClient() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public String sendTransactionalEmail(String apiKey, BrevoEmailRequest emailReq) {
        try {
            return restClient.post()
                    .uri("/smtp/email")
                    .header("api-key", apiKey)
                    .body(emailReq)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new RuntimeException("Brevo API error: " + response.getStatusCode());
                    })
                    .body(String.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute request", e);
        }

    }

}
