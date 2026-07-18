package com.app.shahbaztrades.validator;

import com.app.shahbaztrades.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderValidatorTest {

    @Test
    void validateOrderDate_rejectsPastDate() {
        // A date well in the past is always after its own 09:00 cutoff -> must be rejected.
        assertThrows(BadRequestException.class,
                () -> OrderValidator.validateOrderDate(LocalDate.of(2000, 1, 1)));
    }

    @Test
    void validateOrderDate_allowsFutureDate() {
        assertDoesNotThrow(
                () -> OrderValidator.validateOrderDate(LocalDate.now().plusYears(1)));
    }
}
