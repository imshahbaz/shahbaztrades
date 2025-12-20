package com.shahbaz.trades.controller;

import com.shahbaz.trades.model.dto.request.BrevoEmailRequest;
import com.shahbaz.trades.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/email")
public class EmailController {

    private final EmailService emailService;

    @PostMapping("/send")
    public void sendEmail(@RequestBody BrevoEmailRequest requestl) {
        emailService.sendEmail(requestl);
    }

}
