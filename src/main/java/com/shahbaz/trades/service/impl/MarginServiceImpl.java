package com.shahbaz.trades.service.impl;

import com.shahbaz.trades.model.entity.Margin;
import com.shahbaz.trades.repository.MarginRepository;
import com.shahbaz.trades.service.MarginService;
import com.shahbaz.trades.util.CsvReader;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarginServiceImpl implements MarginService {

    @Value("${spring.margin.leverage}")
    private float leverage;

    private final MarginRepository marginRepository;

    @Override
    public List<Margin> getAllMargins() {
        return List.copyOf(marginMap.values());
    }

    @Override
    public Margin getMargin(String symbol) {
        return marginMap.get(symbol);
    }

    @Override
    @PostConstruct
    public void reloadAllMargins() {
        marginMap.clear();
        marginMap.putAll(marginRepository.findAll().stream()
                .collect(Collectors.toMap(Margin::getSymbol, Function.identity())));
    }

    @Override
    public void loadFromCsv(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException();
        }

        if (!Objects.requireNonNull(file.getOriginalFilename()).endsWith(".csv")) {
            throw new RuntimeException();
        }

        List<Margin> margins = CsvReader.read(file, leverage);

        marginRepository.saveAll(margins);

        marginMap.clear();

        marginMap.putAll(margins.stream()
                .collect(Collectors.toMap(Margin::getSymbol, Function.identity())));
    }
}
