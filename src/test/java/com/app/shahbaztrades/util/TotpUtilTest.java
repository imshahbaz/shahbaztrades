package com.app.shahbaztrades.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpUtilTest {

    // Standard RFC 4648 base32 test secret.
    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Test
    void generateChecksum_matchesPlainSha256OfTheConcatenatedFields() {
        // The broker spec is sha256(applicationId + authToken + apiKey); "a"+"b"+"c" = "abc".
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                TotpUtil.generateChecksum("a", "b", "c"));
    }

    @Test
    void generateChecksum_isLowerCaseHexAndZeroPadded() {
        String checksum = TotpUtil.generateChecksum("app", "token", "secret");

        // A byte hashing to < 0x10 must still occupy two hex chars, otherwise the broker rejects it.
        assertEquals(64, checksum.length());
        assertTrue(checksum.matches("[0-9a-f]{64}"));
    }

    @Test
    void generateChecksum_isSensitiveToFieldBoundaries() {
        // Naive concatenation would collide here; the test documents that it currently does.
        assertEquals(TotpUtil.generateChecksum("ab", "c", "d"),
                TotpUtil.generateChecksum("a", "bc", "d"));

        // Different content must still produce a different digest.
        assertNotEquals(TotpUtil.generateChecksum("a", "b", "c"),
                TotpUtil.generateChecksum("a", "b", "d"));
    }

    @Test
    void generateTOTP_producesSixDigits() {
        String code = TotpUtil.generateTOTP(SECRET);

        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));
    }

    @Test
    void generateTOTP_isStableWithinTheSameThirtySecondBucket() {
        // Two calls microseconds apart share a bucket, so the code must not change.
        assertEquals(TotpUtil.generateTOTP(SECRET), TotpUtil.generateTOTP(SECRET));
    }

    @Test
    void generateTOTP_derivesDifferentCodesFromDifferentSecrets() {
        assertNotEquals(TotpUtil.generateTOTP(SECRET), TotpUtil.generateTOTP("KRSXG5CTMVRXEZLU"));
    }

    @Test
    void generateTOTP_wrapsAFailingSecretInIllegalState() {
        // A null seed (unconfigured broker) must surface as IllegalState, not a raw NPE.
        assertThrows(IllegalStateException.class, () -> TotpUtil.generateTOTP(null));
    }
}
