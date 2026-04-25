package com.app.shahbaztrades.util;

import com.google.gson.Gson;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HelperUtil {
    public static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    public static final Random RANDOM = new Random();
    public static final Gson GSON = new Gson();
    public static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    public static final RestTemplate REST_TEMPLATE = new RestTemplate();
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

}
