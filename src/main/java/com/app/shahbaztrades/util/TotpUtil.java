package com.app.shahbaztrades.util;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)

public class TotpUtil {

    public static String generateTOTP(String secret) {
        try {
            // 1. Provide current system time
            TimeProvider timeProvider = new SystemTimeProvider();

            // 2. Default generator uses HMAC-SHA1, 6 digits (Angel One standard)
            CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);

            // 3. Calculate time bucket (current epoch / 30 seconds)
            long currentBucket = timeProvider.getTime() / 30;

            return codeGenerator.generate(secret, currentBucket);
        } catch (Exception e) {
            // Never return an empty code: a blank TOTP would be silently submitted as a broker
            // 2FA code and mask the real misconfiguration. Fail loudly instead.
            throw new IllegalStateException("Failed to generate TOTP code", e);
        }
    }

    public static String generateChecksum(String applicationId, String authToken, String apiKey) {
        try {
            String combinedString = applicationId + authToken + apiKey;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(combinedString.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm implementation missing from runtime kernel context", e);
        }
    }

}