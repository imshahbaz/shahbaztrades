package com.app.shahbaztrades.components.otp;

import com.app.shahbaztrades.components.brevo.BrevoClient;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.brevo.BrevoEmailRequest;
import com.app.shahbaztrades.model.enums.OtpFor;
import com.app.shahbaztrades.model.enums.OtpType;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.util.CacheUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailOtpStrategy implements OtpStrategy {

    private final StringRedisTemplate redisTemplate;
    private final BrevoClient brevoClient;
    private final MongoConfigService mongoConfigService;

    @Override
    public void send(String email, String otp, OtpFor otpFor) {
        var key = CacheUtils.getOtpCacheKey(email, otpFor);
        var oldOtp = redisTemplate.opsForValue().get(key);
        if (!StringUtils.isEmpty(oldOtp)) {
            throw new ResourceAlreadyExistsException("OTP already sent. Please wait until it expires (5 minutes)");
        }
        var emailReq = BrevoEmailRequest.create(email, otp, otpFor, mongoConfigService.getConfig().getBrevoEmail());
        brevoClient.sendTransactionalEmail(mongoConfigService.getConfig().getBrevoApiKey(), emailReq);
    }

    @Override
    public OtpType getSupportedType() {
        return OtpType.EMAIL;
    }
}