package com.app.shahbaztrades.util;

import com.app.shahbaztrades.model.dto.scheduler.SchedulerCallBackDto;
import com.app.shahbaztrades.model.entity.Margin;
import com.google.gson.Gson;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

@NoArgsConstructor(access = AccessLevel.PRIVATE)

public class HelperUtil {
    public static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    public static final SecureRandom RANDOM = new SecureRandom();
    public static final Gson GSON = new Gson();
    public static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    public static final RestTemplate REST_TEMPLATE = new RestTemplate();
    public static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(5);
    private static final String ALPHA_NUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int OTP_LENGTH = 6;
    private static final String HMAC_ALGO = "HmacSHA256";

    public static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(ALPHA_NUMERIC.length());
            sb.append(ALPHA_NUMERIC.charAt(index));
        }
        return sb.toString();
    }

    public static String generateOtp() {
        int max = (int) Math.pow(10, OTP_LENGTH);
        int number = RANDOM.nextInt(max);
        return String.format("%0" + OTP_LENGTH + "d", number);
    }

    public static String createAuthCookie(String token, int maxAge, boolean isProduction) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from("auth_token", token)
                .maxAge(maxAge)
                .path("/")
                .httpOnly(true)
                .secure(isProduction);

        if (isProduction) {
            builder.sameSite("None");
        }

        return builder.build().toString();
    }

    public static String signState(String uuid, String key) {
        try {
            byte[] hmacKey = key.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec secretKeySpec = new SecretKeySpec(hmacKey, HMAC_ALGO);

            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(secretKeySpec);

            byte[] hmacBytes = mac.doFinal(uuid.getBytes(StandardCharsets.UTF_8));

            String hexSignature = HexFormat.of().formatHex(hmacBytes);

            return uuid + "." + hexSignature;

        } catch (Exception e) {
            throw new RuntimeException("Failed to sign state", e);
        }
    }

    public static String extractAndVerify(String signedCode, String key) {
        if (signedCode == null || !signedCode.contains(".")) {
            return null;
        }

        String[] parts = signedCode.split("\\.");
        if (parts.length != 2) {
            return null;
        }

        String uuid = parts[0];
        String hexSignature = parts[1];

        try {
            byte[] providedSig = HexFormat.of().parseHex(hexSignature);

            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(secretKeySpec);
            byte[] expectedSig = mac.doFinal(uuid.getBytes(StandardCharsets.UTF_8));

            if (!MessageDigest.isEqual(providedSig, expectedSig)) {
                return null;
            }

            return uuid;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean pollWait(long waitMillis) {
        try {
            TimeUnit.MILLISECONDS.sleep(waitMillis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static double fixToTick(double price) {
        double tick;

        if (price < 250) {
            tick = 0.01;
        } else if (price < 1000) {
            tick = 0.05;
        } else if (price < 5000) {
            tick = 0.10;
        } else if (price < 10000) {
            tick = 0.50;
        } else {
            tick = 1.00;
        }

        return Math.round(price / tick) * tick;
    }

    public static ResponseEntity<String> executeCallBack(SchedulerCallBackDto schedulerCallBackDto) {
        var headers = new HttpHeaders();
        if (!CollectionUtils.isEmpty(schedulerCallBackDto.headers())) {
            for (Map.Entry<String, String> header : schedulerCallBackDto.headers().entrySet()) {
                headers.set(header.getKey(), header.getValue());
            }
        }

        Object body = null;
        var method = HttpMethod.valueOf(schedulerCallBackDto.httpMethod());
        if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
            body = schedulerCallBackDto.body();
        }

        if (body == null) {
            body = Collections.emptyMap();
        }

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return REST_TEMPLATE.exchange(schedulerCallBackDto.url(), method, entity, String.class);
    }

    public static void addRupeezyMargin(Map<String, Update> map, String html) {
        Matcher matcher = Constants.RUPEEZY_MARGIN_PATTERN.matcher(html);

        while (matcher.find()) {
            String json = matcher.group()
                    .replace("\\\"", "\"")
                    .replace("\\u0026", "&");

            String symbol = extract(json, "\"symbol\":\"", "\"");
            if (StringUtils.isEmpty(symbol)) {
                continue;
            }

            var update = map.get(symbol);
            if (update == null) {
                continue;
            }

            String leverage = extract(json, "\"margin_multiplier\":", ",");
            String stockName = extract(json, "\"security_desc\":\"", "\"");
            if (!StringUtils.isEmpty(stockName)) {
                update.set(Margin.Fields.name, stockName);
            }

            if (NumberUtils.isCreatable(leverage)) {
                update.set(Margin.Fields.rupeezyMargin, Float.parseFloat(leverage));
            }
        }
    }

    private static String extract(String text, String start, String end) {
        int s = text.indexOf(start);
        if (s == -1) return "";

        s += start.length();
        int e = text.indexOf(end, s);

        if (e == -1) return text.substring(s);
        return text.substring(s, e);
    }

}
