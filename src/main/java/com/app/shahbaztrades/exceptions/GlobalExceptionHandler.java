package com.app.shahbaztrades.exceptions;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private ProblemDetail buildProblem(HttpStatus status, String detail, String title, HttpServletResponse response) {
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        return problemDetail;
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ProblemDetail handleConflict(ResourceAlreadyExistsException ex, HttpServletResponse response) {
        return buildProblem(HttpStatus.CONFLICT, ex.getMessage(), "Conflict", response);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException ex, HttpServletResponse response) {
        return buildProblem(HttpStatus.UNAUTHORIZED, ex.getMessage(), "Unauthorized", response);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneralError(Exception ex, HttpServletResponse response) {
        log.error("An unexpected error occurred", ex);
        return buildProblem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "Internal Server Error", response);
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex, HttpServletResponse response) {
        return buildProblem(HttpStatus.BAD_REQUEST, ex.getMessage(), "Bad Request", response);
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex, HttpServletResponse response) {
        return buildProblem(HttpStatus.NOT_FOUND, ex.getMessage(), "Not Found", response);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex, HttpServletResponse response) {
        return buildProblem(HttpStatus.FORBIDDEN, ex.getMessage(), "Forbidden", response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(HttpServletResponse response) {
        return buildProblem(HttpStatus.NOT_FOUND, "The requested resource was not found.", "Not Found", response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex, HttpServletResponse response) {
        return buildProblem(HttpStatus.BAD_REQUEST, ex.getMessage(), "Invalid Application State", response);
    }

}
