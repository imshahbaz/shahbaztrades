package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.entity.Margin;
import lombok.SneakyThrows;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

public interface MarginService {

    Map<String, Margin> getMarginCache();

    void refreshMargins();

    ResponseEntity<ApiResponse<Collection<Margin>>> getAllMargins();

    ResponseEntity<ApiResponse<Margin>> getMargin(String symbol);

    @SneakyThrows
    void syncMTF(InputStream file);
}
