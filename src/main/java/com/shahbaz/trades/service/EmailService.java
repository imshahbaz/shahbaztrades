package com.shahbaz.trades.service;

import com.shahbaz.trades.model.dto.request.BrevoEmailRequest;

public interface EmailService {

    String SIGNUP_MESSSAGE = """
            <table style="max-width:400px;margin:auto;padding:20px;border:1px solid #ddd;border-radius:8px;">
              <tr>
                <td style="font-family:Arial, sans-serif; font-size:16px;">
                  <p>Your verification code is:</p>
                  <h2 style="color:#1a73e8;">%s</h2>
                  <p>Valid for %d minutes.<br>Do not share this code with anyone.</p>
                  <p>– Shahbaz Trades</p>
                </td>
              </tr>
            </table>
            """;

    void sendEmail(BrevoEmailRequest request);
}
