package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chartink")
@RequiredArgsConstructor
public class ChartInkController {

    @PublicEndpoint
    @GetMapping("/fetchWithMargin")
    public void fetchWithMargin(@RequestParam @NotBlank String strategy) {

    }

}
