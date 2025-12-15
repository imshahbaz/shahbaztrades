package com.shahbaz.trades.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "chartink", url = "https://chartink.com")
public interface ChartinkClient {

    @PostMapping(value = "/screener/process")
    String fetchData(
            @org.springframework.web.bind.annotation.RequestHeader("x-xsrf-token") String xsrfToken,
            @org.springframework.web.bind.annotation.RequestHeader("cookie") String cookie,
            @org.springframework.web.bind.annotation.RequestHeader("user-agent") String userAgent,
            @RequestBody Map<String, String> payload);
}
