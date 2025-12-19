package com.shahbaz.trades.controller.client;

import com.shahbaz.trades.model.dto.UserDto;
import com.shahbaz.trades.model.entity.User;
import com.shahbaz.trades.service.AuthService;
import com.shahbaz.trades.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.session.data.mongo.MongoIndexedSessionRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final MongoIndexedSessionRepository mongoIndexedSessionRepository;

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "logout", required = false) String logout,
                            Model model, HttpServletRequest request, HttpServletResponse response) {
        // Check if user is already logged in
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            // User is already logged in, redirect to main page
            return "redirect:/";
        }

        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully");
        }

        // Prevent browser from caching the login page
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        HttpServletRequest request,
                        Model model) {
        return authService.login(email, password, request, model);
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        // Clear browser cache on logout
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        return "redirect:/login?logout";
    }

    @GetMapping("/signup")
    public String signupPage(HttpServletRequest request) {
        // Check if user is already logged in
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            // User is already logged in, redirect to main page
            return "redirect:/";
        }
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String signup(@ModelAttribute UserDto user, RedirectAttributes redirectAttributes) {
        userService.createUser(user);
        redirectAttributes.addFlashAttribute("message", "Signup successful! Please login.");
        return "redirect:/login";
    }

    @PostMapping("/theme")
    public String updateTheme(@RequestParam String theme, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            UserDto user = (UserDto) session.getAttribute("user");
            User.Theme userTheme = User.Theme.valueOf(theme.toUpperCase());
            userService.updateUserTheme(user.getEmail(), userTheme);
            user.setTheme(userTheme);
            session.setAttribute("user", user);
        }
        return "redirect:" + request.getHeader("Referer");
    }

    @GetMapping("/settings")
    public String settingsPage(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            return "redirect:/login";
        }

        UserDto user = (UserDto) session.getAttribute("user");
        model.addAttribute("user", user);
        return "auth/settings";
    }

    @PostMapping("/settings")
    public String updateSettings(@RequestParam String username,
                                 HttpServletRequest request,
                                 Model model) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            return "redirect:/login";
        }

        UserDto user = (UserDto) session.getAttribute("user");

        try {
            // Update username
            UserDto updatedUser = userService.updateUsername(user.getEmail(), username);
            session.setAttribute("user", updatedUser);
            model.addAttribute("user", updatedUser);
            model.addAttribute("successMessage", "Settings updated successfully!");
        } catch (Exception e) {
            model.addAttribute("user", user);
            model.addAttribute("errorMessage", "Failed to update settings: " + e.getMessage());
        }

        return "auth/settings";
    }

}
