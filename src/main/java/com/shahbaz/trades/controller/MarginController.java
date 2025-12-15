package com.shahbaz.trades.controller;

import com.shahbaz.trades.model.entity.Margin;
import com.shahbaz.trades.service.MarginService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/margin")
public class MarginController {

    private final MarginService marginService;

    @GetMapping("/all")
    public List<Margin> getAllMargins() {
        return marginService.getAllMargins();
    }

    @GetMapping("/{symbol}")
    public Margin getMargin(String symbol) {
        return marginService.getMargin(symbol);
    }

    @GetMapping("/reload")
    public void reloadAllMargins() {
        marginService.reloadAllMargins();
    }

    @PostMapping(value = "/load-from-csv", consumes = "multipart/form-data")
    public void loadFromCsv(@RequestParam MultipartFile file) {
        marginService.loadFromCsv(file);
    }


}
