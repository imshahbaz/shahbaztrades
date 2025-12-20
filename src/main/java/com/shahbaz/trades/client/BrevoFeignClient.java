package com.shahbaz.trades.client;

import com.shahbaz.trades.model.dto.request.BrevoEmailRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "brevoClient",
        url = "https://api.brevo.com"
)
public interface BrevoFeignClient {

    @PostMapping(
            value = "/v3/smtp/email",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    String sendTransactionalEmail(
            @RequestHeader("api-key") String apiKey,
            @RequestBody BrevoEmailRequest request
    );
}