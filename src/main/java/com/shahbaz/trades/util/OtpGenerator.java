package com.shahbaz.trades.util;

import java.security.SecureRandom;

public class OtpGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_LENGTH = 6;

    public static String generateOtp() {
        int bound = (int) Math.pow(10, OTP_LENGTH);
        int otp = RANDOM.nextInt(bound);
        return String.format("%0" + OTP_LENGTH + "d", otp);
    }
}