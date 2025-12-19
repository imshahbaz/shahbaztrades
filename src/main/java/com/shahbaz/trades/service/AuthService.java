package com.shahbaz.trades.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;

public interface AuthService {
    String login(String email, String password, HttpServletRequest request, Model model);
}
