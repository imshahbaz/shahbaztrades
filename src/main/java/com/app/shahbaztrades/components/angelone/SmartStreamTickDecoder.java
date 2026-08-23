package com.app.shahbaztrades.components.angelone;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Decodes AngelOne Smart Stream binary frames. Pure and stateless so the byte layout can be
 * tested without a live socket.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SmartStreamTickDecoder {

    /** Frames shorter than this cannot carry an LTP field. */
    static final int MIN_FRAME_LENGTH = 51;
    /** Subscription mode 1 (LTP). Other modes carry a different layout and are ignored. */
    static final byte LTP_MODE = 1;
    private static final int TOKEN_OFFSET = 2;
    private static final int TOKEN_LENGTH = 25;
    private static final int LTP_OFFSET = 43;
    /** The wire format sends prices in paise. */
    private static final double PAISE_PER_RUPEE = 100.0;

    /**
     * @return the tick, or empty if the frame is truncated, not an LTP frame, or carries a
     * non-positive price (which AngelOne sends for halted or not-yet-traded scrips).
     */
    public static Optional<Tick> decode(ByteBuffer buffer) {
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        if (buffer.remaining() < MIN_FRAME_LENGTH || buffer.get() != LTP_MODE) {
            return Optional.empty();
        }

        byte[] tokenBytes = new byte[TOKEN_LENGTH];
        buffer.position(TOKEN_OFFSET);
        buffer.get(tokenBytes);
        String token = new String(tokenBytes, StandardCharsets.UTF_8).trim();

        double ltp = buffer.getInt(LTP_OFFSET) / PAISE_PER_RUPEE;
        if (ltp <= 0) {
            return Optional.empty();
        }

        return Optional.of(new Tick(token, ltp));
    }

    public record Tick(String token, double ltp) {
    }
}
