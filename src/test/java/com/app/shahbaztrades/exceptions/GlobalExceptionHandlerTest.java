package com.app.shahbaztrades.exceptions;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletResponse response;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void domainExceptionsMapToTheirHttpStatuses() {
        assertEquals(HttpStatus.CONFLICT.value(),
                handler.handleConflict(new ResourceAlreadyExistsException("dup"), response).getStatus());
        assertEquals(HttpStatus.UNAUTHORIZED.value(),
                handler.handleUnauthorized(new UnauthorizedException("no"), response).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST.value(),
                handler.handleBadRequest(new BadRequestException("bad"), response).getStatus());
        assertEquals(HttpStatus.NOT_FOUND.value(),
                handler.handleNotFound(new NotFoundException("gone"), response).getStatus());
        assertEquals(HttpStatus.FORBIDDEN.value(),
                handler.handleForbidden(new ForbiddenException("nope"), response).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST.value(),
                handler.handleIllegalState(new IllegalStateException("state"), response).getStatus());
    }

    @Test
    void domainExceptionsSurfaceTheirOwnMessage() {
        assertEquals("Order not found", handler.handleNotFound(new NotFoundException("Order not found"), response)
                .getDetail());
    }

    @Test
    void everyHandlerSetsTheProblemJsonContentType() {
        handler.handleBadRequest(new BadRequestException("bad"), response);
        verify(response).setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    }

    @Test
    void unexpectedExceptionsAre500AndDoNotLeakInternals() {
        // Stack traces and driver messages must never reach the client.
        var problem = handler.handleGeneralError(
                new RuntimeException("mongodb://user:password@host failed"), response);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
        assertEquals("An unexpected error occurred", problem.getDetail());
    }

    @Test
    void bodyValidationErrorsAreFlattenedIntoOneDetailString() throws Exception {
        var binding = new BeanPropertyBindingResult(new Object(), "orderDto");
        binding.addError(new FieldError("orderDto", "symbol", "must not be blank"));
        binding.addError(new FieldError("orderDto", "quantity", "must be at least 1"));
        var exception = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(
                        GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class), 0),
                binding);

        var problem = handler.handleBodyValidation(exception, response);

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertTrue(problem.getDetail().contains("symbol: must not be blank"));
        assertTrue(problem.getDetail().contains("quantity: must be at least 1"));
    }

    @Test
    void constraintViolationsAreFlattenedIntoOneDetailString() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("getMargin.symbol");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be blank");

        var problem = handler.handleConstraintViolation(
                new ConstraintViolationException(Set.of(violation)), response);

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertTrue(problem.getDetail().contains("getMargin.symbol: must not be blank"));
    }

    @Test
    void unreadableBodiesAndTypeMismatchesAre400WithASafeMessage() {
        assertEquals("Malformed or unreadable request body",
                handler.handleUnreadableBody(response).getDetail());

        var mismatch = mock(MethodArgumentTypeMismatchException.class);
        when(mismatch.getName()).thenReturn("brokerType");
        assertEquals("Invalid value for parameter 'brokerType'",
                handler.handleTypeMismatch(mismatch, response).getDetail());
    }

    @Test
    void unknownRoutesAre404() {
        assertEquals(HttpStatus.NOT_FOUND.value(), handler.handleNoResourceFound(response).getStatus());
    }

    @SuppressWarnings("unused")
    private void dummy(String value) {
        // Target for the MethodParameter used by the validation test above.
    }
}
