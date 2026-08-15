package com.app.shahbaztrades.util;

import com.app.shahbaztrades.exceptions.UnauthorizedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConstantsTest {

    @Test
    void validateSessionCallback_acceptsTheExactSourceHeader() {
        assertDoesNotThrow(() -> Constants.validateSessionCallback(Constants.SESSION_MANAGER_SOURCE));
    }

    @Test
    void validateSessionCallback_rejectsMissingHeader() {
        assertThrows(UnauthorizedException.class, () -> Constants.validateSessionCallback(null));
        assertThrows(UnauthorizedException.class, () -> Constants.validateSessionCallback(""));
    }

    @Test
    void validateSessionCallback_rejectsWrongOrDifferentlyCasedSource() {
        // The header gates an unauthenticated callback that can place orders: match must be exact.
        assertThrows(UnauthorizedException.class, () -> Constants.validateSessionCallback("Session-Manager"));
        assertThrows(UnauthorizedException.class, () -> Constants.validateSessionCallback("someone-else"));
    }
}
