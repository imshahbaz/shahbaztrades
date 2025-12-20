package com.shahbaz.trades.exceptions;

public class DuplicateOtpRequest extends RuntimeException {
    public DuplicateOtpRequest(String message) {
        super(message);
    }
}
