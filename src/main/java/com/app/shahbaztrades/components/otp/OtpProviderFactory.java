package com.app.shahbaztrades.components.otp;

import com.app.shahbaztrades.model.enums.OtpFor;
import com.app.shahbaztrades.model.enums.OtpType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OtpProviderFactory {

    private final Map<OtpType, OtpStrategy> strategies;

    public OtpProviderFactory(List<OtpStrategy> strategyList) {
        strategies = strategyList.stream()
                .collect(Collectors.toMap(OtpStrategy::getSupportedType, s -> s));
    }

    public void sendOtp(OtpType type, String target, String otp, OtpFor otpFor) {
        OtpStrategy strategy = strategies.get(type);
        if (strategy == null) throw new IllegalArgumentException("No provider for " + type);
        strategy.send(target, otp, otpFor);
    }
}