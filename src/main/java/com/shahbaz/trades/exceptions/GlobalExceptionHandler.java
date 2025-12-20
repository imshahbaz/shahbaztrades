package com.shahbaz.trades.exceptions;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public String handleUserNotFound(UserNotFoundException ex, Model model) {
        model.addAttribute("error", ex.getMessage());
        return "auth/login";
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public String handleInvalidCredentials(AuthenticationFailedException ex, Model model) {
        model.addAttribute("error", ex.getMessage());
        return "auth/login";
    }

    @ExceptionHandler(Exception.class)
    public String handleOtherExceptions(Exception ex, Model model) {
        model.addAttribute("error", "Something went wrong");
        return "auth/login";
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public String handleUserAlreadyExists(UserAlreadyExistsException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
        return "redirect:/login";
    }

    @ExceptionHandler(InvalidOtpException.class)
    public String handleInvalidOtp(InvalidOtpException ex, Model model) {
        model.addAttribute("otpSent", true);
        model.addAttribute("error", ex.getMessage());
        return "auth/signup";
    }

    @ExceptionHandler(DuplicateOtpRequest.class)
    public String handleDuplicateOtp(DuplicateOtpRequest ex, Model model) {
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("otpSent", true);
        return "auth/signup";
    }

}
