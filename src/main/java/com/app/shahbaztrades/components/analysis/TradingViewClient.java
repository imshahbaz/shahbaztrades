package com.app.shahbaztrades.components.analysis;

import com.app.shahbaztrades.model.dto.analysis.TradingViewNewsResponse;
import com.app.shahbaztrades.util.HelperUtil;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

public class TradingViewClient {

    public static String newsEndpoint = "https://news-mediator.tradingview.com/public/news-flow/v2/news";

    public static TradingViewNewsResponse getStockNews(String symbol) {
        URI uri = UriComponentsBuilder.fromUriString(newsEndpoint)
                .queryParam("client", "chart").queryParam("user_prostatus", "non_pro")
                .queryParam("filter", "lang:en").queryParam("filter", "symbol:NSE:" + symbol)
                .build().toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.REFERER, "https://in.tradingview.com/");
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1");

        var res = HelperUtil.REST_TEMPLATE.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), TradingViewNewsResponse.class);

        if (res.getStatusCode().is2xxSuccessful()) {
            return res.getBody();
        }

        return null;
    }
}
