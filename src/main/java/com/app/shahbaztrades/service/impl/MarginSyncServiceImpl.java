package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.angelone.AngelOneClient;
import com.app.shahbaztrades.components.rupeezy.RupeezyMarginParser;
import com.app.shahbaztrades.components.rupeezy.RupeezyWebClient;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.repo.MarginRepo;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.service.MarginSyncService;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.util.Constants;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mongodb.client.result.DeleteResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarginSyncServiceImpl implements MarginSyncService {

    private final MarginRepo marginRepo;
    private final MarginService marginService;
    private final MongoConfigService mongoConfigService;
    private final JsonMapper jsonMapper;
    private final MongoTemplate mongoTemplate;
    private final AngelOneClient angelOneClient;
    private final RupeezyWebClient rupeezyWebClient;

    @Override
    @SneakyThrows
    @Async("taskExecutor")
    public void syncMTF(byte[] fileBytes) {
        float minLeverage = mongoConfigService.getConfig().getLeverage();
        try (InputStream file = new ByteArrayInputStream(fileBytes)) {
            MappingIterator<RawMTF> it =
                    jsonMapper.readerFor(RawMTF.class).readValues(file);

            Map<String, Update> updates = new HashMap<>();
            BulkOperations bulkOperations =
                    mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Margin.class);

            while (it.hasNext()) {
                RawMTF raw = it.next();

                if (raw.leverage >= minLeverage) {
                    Query query = new Query(
                            Criteria.where(Margin.Fields.symbol).is(raw.tradingSymbol)
                    );

                    Update update = new Update();
                    update.set(Margin.Fields.requiredMargin, BigDecimal.valueOf(raw.leverage));
                    update.set(Margin.Fields.name, raw.tradingSymbol);

                    bulkOperations.upsert(query, update);
                    updates.put(raw.tradingSymbol, update);
                }
            }

            if (!updates.isEmpty()) {
                try {
                    String html = rupeezyWebClient.getMtfStockListPage(Constants.DEFAULT_UA);
                    RupeezyMarginParser.addRupeezyMargin(updates, html);
                } catch (Exception e) {
                    log.error("Error while updating rupeezy margins", e);
                }

                bulkOperations.execute();

                Query query = new Query(
                        Criteria.where(Margin.Fields.symbol).nin(updates.keySet())
                );

                DeleteResult result = mongoTemplate.remove(query, Margin.class);

                marginService.refreshMargins();

                log.info("{} Loaded. Cache updated. Symbols synced: {}. Deleted stale: {}",
                        "Mtf Json",
                        updates.size(),
                        result.getDeletedCount()
                );
            }
        }
    }

    @Override
    @Async("taskExecutor")
    public void syncAngelOneToken() {
        var margins = angelOneClient.getTokens(marginService.getMarginCache());
        if (CollectionUtils.isEmpty(margins)) {
            return;
        }

        marginRepo.saveAll(margins);
        marginService.refreshMargins();
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
