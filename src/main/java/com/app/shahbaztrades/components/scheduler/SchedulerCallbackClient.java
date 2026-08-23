package com.app.shahbaztrades.components.scheduler;

import com.app.shahbaztrades.model.dto.scheduler.SchedulerCallBackDto;
import com.app.shahbaztrades.util.HttpUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;

import java.util.Map;

/** Invokes the callback a scheduled task was registered with. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SchedulerCallbackClient {

    public static ResponseEntity<String> execute(SchedulerCallBackDto callBack) {
        var headers = new HttpHeaders();
        if (!CollectionUtils.isEmpty(callBack.headers())) {
            for (Map.Entry<String, String> header : callBack.headers().entrySet()) {
                headers.set(header.getKey(), header.getValue());
            }
        }

        // Only the body-bearing methods carry the stored payload.
        var method = HttpMethod.valueOf(callBack.httpMethod());
        Object body = null;
        if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
            body = callBack.body();
        }

        return HttpUtil.REST_TEMPLATE.exchange(callBack.url(), method, new HttpEntity<>(body, headers), String.class);
    }
}
