package com.app.shahbaztrades.components.otp;

import com.app.shahbaztrades.model.enums.OtpFor;
import com.app.shahbaztrades.model.enums.OtpType;

public interface OtpStrategy {
    void send(String target, String otp, OtpFor otpFor);

    OtpType getSupportedType();
}