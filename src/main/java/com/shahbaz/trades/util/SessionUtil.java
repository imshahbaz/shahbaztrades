package com.shahbaz.trades.util;

import com.shahbaz.trades.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

@Component
public class SessionUtil {

    public static User getCurrentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            return (User) session.getAttribute("user");
        }
        return null;
    }

    public static boolean isLoggedIn(HttpServletRequest request) {
        return getCurrentUser(request) != null;
    }

    public static void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
