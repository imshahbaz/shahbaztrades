package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.angelone.AngelOneClient;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.repo.MarginRepo;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.service.MongoConfigService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mongodb.client.result.DeleteResult;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarginServiceImpl implements MarginService {

    private final MarginRepo marginRepo;
    private final MongoConfigService mongoConfigService;
    private final JsonMapper jsonMapper;
    private final MongoTemplate mongoTemplate;
    private final AngelOneClient angelOneClient;
    private Map<String, Margin> cachedMargins = new HashMap<>();

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
    public ResponseEntity<ApiResponse<Collection<Margin>>> getAllMargins() {
        return ResponseEntity.ok(ApiResponse.ok(cachedMargins.values(), "Success"));
    }

    @Override
    public ResponseEntity<ApiResponse<Margin>> getMargin(String symbol) {
        var margin = cachedMargins.get(symbol);
        if (margin == null) {
            throw new NotFoundException("Margin not found");
        }
        return ResponseEntity.ok(ApiResponse.ok(margin, "Success"));
    }

    @SneakyThrows
    @Override
    public void syncMTF(InputStream file) {
        float minLeverage = mongoConfigService.getConfig().getLeverage();
        MappingIterator<RawMTF> it = jsonMapper.readerFor(RawMTF.class).readValues(file);

        List<Margin> toSave = new ArrayList<>();

        while (it.hasNext()) {
            RawMTF raw = it.next();
            if (raw.leverage >= minLeverage) {
                toSave.add(Margin.builder()
                        .symbol(raw.tradingSymbol)
                        .name(raw.tradingSymbol)
                        .margin(raw.leverage)
                        .build());
            }
        }

        if (!toSave.isEmpty()) {
            marginRepo.saveAll(toSave);
            Query query = new Query(Criteria.where("_id").nin(toSave.stream().map(Margin::getSymbol).collect(Collectors.toList())));
            DeleteResult result = mongoTemplate.remove(query, Margin.class);
            refreshMargins();
            log.info("{} Loaded. Cache updated. Symbols synced: {}. Deleted stale: {}",
                    "Mtf Json",
                    toSave.size(),
                    result.getDeletedCount());
        }
    }

    @Override
    public void syncAngelOneToken() {
        var margins = angelOneClient.getTokens(cachedMargins);
        if (CollectionUtils.isEmpty(margins)) {
            return;
        }

        marginRepo.saveAll(margins);
        refreshMargins();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RawMTF {

        @JsonProperty("tradingsymbol")
        String tradingSymbol;

        @JsonProperty("leverage")
        float leverage;
    }

}
