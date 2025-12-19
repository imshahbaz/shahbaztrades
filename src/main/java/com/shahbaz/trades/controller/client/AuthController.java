package com.shahbaz.trades.controller.client;

import com.shahbaz.trades.model.dto.UserDto;
import com.shahbaz.trades.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signupPage() {
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String signup(@ModelAttribute UserDto user, Model model) {
        try {
            userService.createUser(user);
            return "redirect:/login";
        }catch (Exception e) {
            model.addAttribute("error", "Username already exists");
            return "signup";
        }
    }

}
