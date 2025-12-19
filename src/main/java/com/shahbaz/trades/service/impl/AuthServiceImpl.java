package com.shahbaz.trades.service.impl;

import com.shahbaz.trades.model.entity.User;
import com.shahbaz.trades.repository.UserRepository;
import com.shahbaz.trades.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;

    @Override
    public String login(String email, String password, HttpServletRequest request, Model model) {
        try {
            User user = userRepository.findById(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (BCrypt.checkpw(password, user.getPassword())) {
                HttpSession session = request.getSession();
                session.setAttribute("user", user.toDto());
                return "redirect:/";
            } else {
                model.addAttribute("error", "Invalid email or password");
                return "auth/login";
            }
        } catch (Exception e) {
            model.addAttribute("error", "Invalid email or password");
            return "auth/login";
        }
    }
}
