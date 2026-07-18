package com.app.shahbaztrades.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelperUtilTest {

    @Test
    void fixToTick_snapsToPennyTickBelow250() {
        // tick = 0.01 for price < 250
        assertEquals(100.07, HelperUtil.fixToTick(100.071), 1e-9);
        assertEquals(100.05, HelperUtil.fixToTick(100.054), 1e-9);
    }

    @Test
    void fixToTick_snapsToFivePaisaTickBetween250And1000() {
        // tick = 0.05 for 250 <= price < 1000
        assertEquals(500.05, HelperUtil.fixToTick(500.07), 1e-9);
        assertEquals(500.10, HelperUtil.fixToTick(500.08), 1e-9);
    }

    @Test
    void fixToTick_snapsToTenPaisaTickBetween1000And5000() {
        assertEquals(1500.10, HelperUtil.fixToTick(1500.13), 1e-9);
        assertEquals(1500.20, HelperUtil.fixToTick(1500.16), 1e-9);
    }

    @Test
    void fixToTick_snapsToRupeeTickAbove10000() {
        assertEquals(12345.0, HelperUtil.fixToTick(12345.4), 1e-9);
        assertEquals(12346.0, HelperUtil.fixToTick(12345.6), 1e-9);
    }

    @Test
    void generateOtp_isAlwaysSixDigits() {
        for (int i = 0; i < 1000; i++) {
            String otp = HelperUtil.generateOtp();
            assertEquals(6, otp.length(), "OTP must be zero-padded to 6 digits");
            assertTrue(otp.chars().allMatch(Character::isDigit));
        }
    }

    @Test
    void generateRandomString_hasRequestedLength() {
        assertEquals(20, HelperUtil.generateRandomString(20).length());
    }
}
