package com.app.shahbaztrades.components.angelone;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The binary layout that decides every price in the system. Previously reachable only through a
 * live websocket, so none of these cases were covered.
 */
class SmartStreamTickDecoderTest {

    /** Builds an LTP-mode frame the way AngelOne sends it: mode byte, token at 2, price in paise at 43. */
    private static ByteBuffer frame(byte mode, String token, int ltpInPaise, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(0, mode);
        buffer.put(2, token.getBytes(StandardCharsets.UTF_8));
        buffer.putInt(43, ltpInPaise);
        return buffer;
    }

    private static ByteBuffer validFrame(String token, int ltpInPaise) {
        return frame((byte) 1, token, ltpInPaise, 51);
    }

    @Test
    void decodesTheTokenAndConvertsPaiseToRupees() {
        var tick = SmartStreamTickDecoder.decode(validFrame("11536", 320050)).orElseThrow();

        assertEquals("11536", tick.token());
        assertEquals(3200.50, tick.ltp(), 1e-9);
    }

    @Test
    void trimsThePaddingFromTheFixedWidthTokenField() {
        // The token field is 25 bytes; everything past the id is NUL padding.
        var tick = SmartStreamTickDecoder.decode(validFrame("1594", 100)).orElseThrow();

        assertEquals("1594", tick.token());
    }

    @Test
    void rejectsATruncatedFrame() {
        assertTrue(SmartStreamTickDecoder.decode(frame((byte) 1, "11536", 320050, 50)).isEmpty());
    }

    @Test
    void rejectsAFrameThatIsNotLtpMode() {
        // Quote and snap-quote frames put different fields at offset 43.
        assertTrue(SmartStreamTickDecoder.decode(frame((byte) 2, "11536", 320050, 51)).isEmpty());
    }

    @Test
    void rejectsAZeroPriceFromAScripThatHasNotTradedYet() {
        assertTrue(SmartStreamTickDecoder.decode(validFrame("11536", 0)).isEmpty());
    }

    @Test
    void rejectsANegativePrice() {
        assertTrue(SmartStreamTickDecoder.decode(validFrame("11536", -100)).isEmpty());
    }

    @Test
    void readsTheSmallestTickAboveZero() {
        var tick = SmartStreamTickDecoder.decode(validFrame("11536", 1)).orElseThrow();

        assertEquals(0.01, tick.ltp(), 1e-9);
    }
}
