package com.shahbaz.trades.service.impl;

import com.shahbaz.trades.exceptions.AuthenticationFailedException;
import com.shahbaz.trades.exceptions.UserNotFoundException;
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
        User user = userRepository.findById(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new AuthenticationFailedException("Invalid email or password");
        }

        // 1️⃣ Kill existing session (fixation protection)
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }

        // 2️⃣ Create new session
        HttpSession newSession = request.getSession(true);
        newSession.setAttribute("user", user.toDto());

        return "redirect:/";
    }
}
