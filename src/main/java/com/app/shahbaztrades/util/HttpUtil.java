package com.app.shahbaztrades.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

/** The shared outbound HTTP stack. One connection pool serves every broker and data client. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HttpUtil {

    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static final RestTemplate REST_TEMPLATE = new RestTemplate(requestFactory(Duration.ofSeconds(15)));

    /** Read timeouts differ per upstream, so each client supplies its own. */
    public static JdkClientHttpRequestFactory requestFactory(Duration readTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HTTP_CLIENT);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
