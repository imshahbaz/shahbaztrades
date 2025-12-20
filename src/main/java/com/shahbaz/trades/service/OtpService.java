package com.shahbaz.trades.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.shahbaz.trades.model.dto.UserDto;

import java.time.Duration;

public interface OtpService {

    Cache<String, String> otpCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    void sendSignUpOtp(UserDto request);

    boolean verifyOtp(String email, String otp);
}
