package com.app.shahbaztrades.util;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;

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
            return "";
        }
    }

}