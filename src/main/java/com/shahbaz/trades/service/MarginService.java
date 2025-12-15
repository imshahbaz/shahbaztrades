package com.shahbaz.trades.service;

import com.shahbaz.trades.model.entity.Margin;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface MarginService {

    Map<String, Margin> marginMap = new ConcurrentHashMap<>();

    List<Margin> getAllMargins();

    Margin getMargin(String symbol);

    void reloadAllMargins();

    void loadFromCsv(MultipartFile file);

}
