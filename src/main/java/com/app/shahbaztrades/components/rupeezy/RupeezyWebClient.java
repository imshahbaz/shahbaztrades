package com.app.shahbaztrades.components.rupeezy;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "rupeezy-web-client", url = "https://rupeezy.in")
public interface RupeezyWebClient {

    @GetMapping(
            value = "/margin-trading-facility/mtf-stock-lists",
            produces = "text/html"
    )
    String getMtfStockListPage(@RequestHeader("User-Agent") String userAgent);
}