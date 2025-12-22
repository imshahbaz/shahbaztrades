package com.shahbaz.trades.controller.client;

import com.shahbaz.trades.model.dto.UserDto;
import com.shahbaz.trades.model.entity.User;
import com.shahbaz.trades.service.MarginService;
import com.shahbaz.trades.service.StrategyService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final StrategyService strategyService;
    private final MarginService marginService;

    @GetMapping("/")
    public String index() {
        return "home";
    }

    @GetMapping("/strategies")
    public String strategies(Model model) {
        model.addAttribute("strategies", strategyService.getAllStrategy());
        return "strategies";
    }

    @GetMapping("/calculator")
    public String calculator(Model model) {
        model.addAttribute("margins", marginService.getAllMargins());
        return "calculator";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(HttpSession session, Model model) {
        UserDto user = (UserDto) session.getAttribute("user");
        if (user == null || user.getRole() != User.Role.ADMIN) {
            return "redirect:/";
        }
        return "admin/dashboard";
    }
}
