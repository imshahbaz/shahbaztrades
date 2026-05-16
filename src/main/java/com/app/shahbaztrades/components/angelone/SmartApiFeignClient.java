package com.app.shahbaztrades.components.angelone;

import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpDto;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "smart-api-client", url = "https://apiconnect.angelone.in")
public interface SmartApiFeignClient {

    @PostMapping(
            value = "/rest/secure/angelbroking/market/v1/quote/",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE,
            headers = {
                    "X-SourceID=WEB",
                    "X-UserType=USER",
                    "X-ClientLocalIP=127.0.0.1",
                    "X-ClientPublicIP=127.0.0.1",
                    "X-MACAddress=00:00:00:00:00:00"
            }
    )
    SmartApiLtpResponse getMultipleLtp(
            @RequestHeader("Authorization") String jwtToken,
            @RequestHeader("X-PrivateKey") String privateKey,
            @RequestBody SmartApiLtpDto request);

}
