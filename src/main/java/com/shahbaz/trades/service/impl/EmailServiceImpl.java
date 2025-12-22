package com.shahbaz.trades.service.impl;

import com.shahbaz.trades.client.BrevoFeignClient;
import com.shahbaz.trades.config.env.SystemConfigs;
import com.shahbaz.trades.model.dto.request.BrevoEmailRequest;
import com.shahbaz.trades.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final BrevoFeignClient brevoFeignClient;
    private final SystemConfigs systemConfigs;

    @Override
    public void sendEmail(BrevoEmailRequest request) {
        brevoFeignClient.sendTransactionalEmail(systemConfigs.getConfig().getBrevoApiKey(), request);
    }

}
