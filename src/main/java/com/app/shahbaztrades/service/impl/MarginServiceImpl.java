package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.repo.MarginRepo;
import com.app.shahbaztrades.service.MarginService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarginServiceImpl implements MarginService {

    private final MarginRepo marginRepo;
    private Map<String, Margin> cachedMargins = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshMargins();
    }

    @Override
    public Map<String, Margin> getMarginCache() {
        return cachedMargins;
    }

    @Override
    public void refreshMargins() {
        cachedMargins = marginRepo.findAll().stream()
                .collect(Collectors.toMap(
                        Margin::getSymbol,
                        margin -> margin
                ));
        log.info("Refreshed margins for {} margins.", cachedMargins.size());
    }

    @Override
    public Collection<Margin> getAllMargins() {
        return cachedMargins.values();
    }

    @Override
    public Margin getMargin(String symbol) {
        var margin = cachedMargins.get(symbol);
        if (margin == null) {
            throw new NotFoundException("Margin not found");
        }
        return margin;
    }

}
