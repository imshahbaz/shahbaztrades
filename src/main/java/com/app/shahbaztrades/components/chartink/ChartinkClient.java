package com.app.shahbaztrades.components.chartink;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ChartinkClient {

    private final RestClient restClient;
    private static final String tokenKey = "XSRF-TOKEN";
    private static final String BASE_URL = "https://chartink.com";
    private static final String DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private volatile String cookieHeaderValue = "";

    public ChartinkClient() {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Accept", "application/json")
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
                        java.net.http.HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(30))
                                .build()
                ))
                .build();
    }

    public String fetchCsrfToken() {
        Connection.Response res;
        try {
            res = Jsoup.connect(BASE_URL)
                    .userAgent(DEFAULT_UA)
                    .method(Connection.Method.GET)
                    .execute();
        } catch (IOException e) {
            return null;
        }

        String rawCookie = res.cookie(tokenKey);
        if (rawCookie == null) {
            return null;
        }

        cookieHeaderValue = res.cookies().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));

        return URLDecoder.decode(rawCookie, StandardCharsets.UTF_8);
    }

    public String fetchData(String token, Map<String, String> payload) {
        return executePost("/screener/process", token, payload);
    }

    public String fetchBackTestData(String token, Map<String, String> payload) {
        return executePost("/backtest/process", token, payload);
    }

    private String executePost(String endpoint, String token, Map<String, String> payload) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        payload.forEach(formData::add);

        return restClient.post()
                .uri(endpoint)
                .header("X-XSRF-TOKEN", token)
                .header("Cookie", cookieHeaderValue)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", BASE_URL + "/")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(String.class);
    }
}