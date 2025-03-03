package com.shahbaztrades.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class HomeController {

    @GetMapping({"/", "/home"})
    public ModelAndView showLoginPage(){
        ModelAndView view =  new ModelAndView("home/home");
        view.addObject("name","Shahbaz Trades");
        return view;
    }
}
