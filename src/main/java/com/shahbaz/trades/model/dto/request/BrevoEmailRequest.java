package com.shahbaz.trades.model.dto.request;

import com.shahbaz.trades.service.EmailService;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BrevoEmailRequest {
    Recipient sender;
    List<Recipient> to;
    String subject;
    String htmlContent;

    @Data
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Recipient {
        String email;
        String name;
    }

    public void signup(String otp, int validity) {
        this.subject = "Signup Verification Code";
        this.htmlContent = String.format(EmailService.SIGNUP_MESSSAGE, otp, validity);
    }

}
