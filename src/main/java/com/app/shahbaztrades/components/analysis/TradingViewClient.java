package com.app.shahbaztrades.components.analysis;

import com.app.shahbaztrades.util.HttpUtil;
import com.app.shahbaztrades.model.dto.analysis.TradingViewNewsResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/** Reads TradingView's public news feed. A bean, so callers can stand it in for a stub. */
@Component
public class TradingViewClient {

    private static final String NEWS_ENDPOINT = "https://news-mediator.tradingview.com/public/news-flow/v2/news";

    /** @return the feed payload, or null if TradingView answered with a non-2xx status. */
    public TradingViewNewsResponse getStockNews(String symbol) {
        URI uri = UriComponentsBuilder.fromUriString(NEWS_ENDPOINT)
                .queryParam("client", "chart").queryParam("user_prostatus", "non_pro")
                .queryParam("filter", "lang:en").queryParam("filter", "symbol:NSE:" + symbol)
                .build().toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.REFERER, "https://in.tradingview.com/");
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1");

        var res = HttpUtil.REST_TEMPLATE.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), TradingViewNewsResponse.class);

        if (res.getStatusCode().is2xxSuccessful()) {
            return res.getBody();
        }

        return null;
    }
}
