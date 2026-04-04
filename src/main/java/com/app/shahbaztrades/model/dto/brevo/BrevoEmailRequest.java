package com.app.shahbaztrades.model.dto.brevo;

import com.app.shahbaztrades.model.enums.OtpFor;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BrevoEmailRequest {
    Recipient sender;
    List<Recipient> to;
    String subject;
    String htmlContent;

    public static BrevoEmailRequest create(String email, String otp, OtpFor type, String senderEmail) {
        String userName = email.split("@")[0];
        BrevoEmailRequest request = BrevoEmailRequest.builder()
                .sender(Recipient.builder()
                        .email(senderEmail)
                        .name("Shahbaz Trades")
                        .build())
                .to(List.of(Recipient.builder()
                        .email(email)
                        .name(userName)
                        .build()))
                .build();

        switch (type) {
            case REGISTER -> request.applySignupTemplate(otp, 5);
            case UPDATE -> request.applyUpdateTemplate(otp, 5);
        }

        return request;
    }

    private void applySignupTemplate(String otp, int expiryMinutes) {
        this.subject = "Welcome to Shahbaz Trades - Verify your Email";
        this.htmlContent = String.format("Your OTP for signup is: <b>%s</b>. Valid for %d minutes.", otp, expiryMinutes);
    }

    private void applyUpdateTemplate(String otp, int expiryMinutes) {
        this.subject = "Action Required: Verify your Email Update";
        this.htmlContent = String.format("Your OTP for updating your email is: <b>%s</b>. Valid for %d minutes.", otp, expiryMinutes);
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Recipient {
        String email;
        String name;
    }

}