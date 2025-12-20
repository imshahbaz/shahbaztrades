package com.shahbaz.trades.service.impl;

import com.shahbaz.trades.config.SystemConfigs;
import com.shahbaz.trades.model.dto.UserDto;
import com.shahbaz.trades.model.dto.request.BrevoEmailRequest;
import com.shahbaz.trades.service.EmailService;
import com.shahbaz.trades.service.OtpService;
import com.shahbaz.trades.util.OtpGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final EmailService emailService;
    private final SystemConfigs systemConfigs;

    @Override
    public void sendSignUpOtp(UserDto request) {
        String otp = otpCache.getIfPresent(request.getEmail());
        if (otp != null) {
           throw new RuntimeException("Already requested an otp for this email,please try after 5 minutes");
        }

        otp = OtpGenerator.generateOtp();

        BrevoEmailRequest emailRequest = BrevoEmailRequest.builder()
                .sender(
                        BrevoEmailRequest.Recipient.builder()
                                .email(systemConfigs.getConfig().getBrevoEmail())
                                .name("Shahbaz Trades")
                                .build()
                )
                .to(List.of(
                        BrevoEmailRequest.Recipient.builder()
                                .email(request.getEmail())
                                .name(request.getEmail().split("@")[0])
                                .build()
                ))
                .build();
        emailRequest.signup(otp,5);

        emailService.sendEmail(emailRequest);

        otpCache.put(request.getEmail(),otp);

    }

}
