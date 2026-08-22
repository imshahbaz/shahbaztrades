package com.app.shahbaztrades.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Credential and session primitives: hashing, secrets, signed state and the auth cookie. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuthUtil {

    public static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    public static final SecureRandom RANDOM = new SecureRandom();

    private static final String ALPHA_NUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int OTP_LENGTH = 6;
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String AUTH_COOKIE = "auth_token";

    public static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHA_NUMERIC.charAt(RANDOM.nextInt(ALPHA_NUMERIC.length())));
        }
        return sb.toString();
    }

    public static String createAuthCookie(String token, int maxAge, boolean isProduction) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(AUTH_COOKIE, token)
                .maxAge(maxAge)
                .path("/")
                .httpOnly(true)
                .secure(isProduction);

        // SameSite=None needs Secure, so it is only safe to set in production.
        if (isProduction) {
            builder.sameSite("None");
        }

        return builder.build().toString();
    }

    /** Signs state handed to a third party so it can be trusted when it comes back. */
    public static String signState(String uuid, String key) {
        try {
            return uuid + "." + HexFormat.of().formatHex(hmac(uuid, key));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign state", e);
        }
    }

    /** @return the original value, or null if the signature is absent, malformed or forged. */
    public static String extractAndVerify(String signedCode, String key) {
        if (signedCode == null || !signedCode.contains(".")) {
            return null;
        }

        String[] parts = signedCode.split("\\.");
        if (parts.length != 2) {
            return null;
        }

        String uuid = parts[0];
        try {
            byte[] providedSig = HexFormat.of().parseHex(parts[1]);
            // Constant-time compare: a byte-by-byte one leaks the signature through timing.
            return MessageDigest.isEqual(providedSig, hmac(uuid, key)) ? uuid : null;
        } catch (Exception _) {
            return null;
        }
    }

    private static byte[] hmac(String value, String key) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }
}
