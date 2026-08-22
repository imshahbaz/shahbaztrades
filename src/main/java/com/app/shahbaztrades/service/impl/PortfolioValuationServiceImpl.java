package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.trading.BrokerMarginPolicyFactory;
import com.app.shahbaztrades.model.entity.Holdings;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.repo.redis.HoldingsDataRedisRepo;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.service.MarketDataQuery;
import com.app.shahbaztrades.service.PortfolioValuationService;
import com.app.shahbaztrades.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioValuationServiceImpl implements PortfolioValuationService {

    private final MongoTemplate mongoTemplate;
    private final MarginService marginService;
    private final MarketDataQuery marketDataQuery;
    private final BrokerMarginPolicyFactory brokerMarginPolicyFactory;
    private final HoldingsDataRedisRepo<Holdings> holdingsDataRedisRepo;

    private static String brokerField(BrokerType broker) {
        return Holdings.Fields.brokerHoldingMap + Constants.DOT + broker.name();
    }

    @Override
    @Async("taskExecutor")
    public void updatePortfolio() {
        var holdings = mongoTemplate.find(usersHoldingAnything(), Holdings.class);
        if (CollectionUtils.isEmpty(holdings)) {
            return;
        }

        // One lookup per symbol serves every user holding it, including a failed lookup.
        Map<String, Double> ltpCache = new HashMap<>();
        var revaluedUsers = new ArrayList<String>(holdings.size());
        var bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Holdings.class);

        for (var userHoldings : holdings) {
            var update = revalue(userHoldings, ltpCache);
            if (update == null) {
                continue;
            }

            bulkOps.updateOne(
                    new Query(Criteria.where(Constants.MONGO_ID).is(userHoldings.getUserId())), update);
            revaluedUsers.add(String.valueOf(userHoldings.getUserId()));
        }

        if (!revaluedUsers.isEmpty()) {
            bulkOps.execute();
            holdingsDataRedisRepo.deleteAll(revaluedUsers);
        }
    }

    /** Skips users whose holdings map is empty, rather than loading the whole collection. */
    private Query usersHoldingAnything() {
        var criteria = Arrays.stream(BrokerType.values())
                .map(broker -> Criteria.where(brokerField(broker)).exists(true))
                .toArray(Criteria[]::new);
        return new Query(new Criteria().orOperator(criteria));
    }

    /**
     * @return an update touching only the broker sub-arrays this user actually holds, so one
     * broker's positions are never overwritten by another's; null when the user holds nothing.
     */
    private Update revalue(Holdings userHoldings, Map<String, Double> ltpCache) {
        Update update = null;

        for (var entry : userHoldings.getBrokerHoldingMap().entrySet()) {
            var positions = entry.getValue();
            if (CollectionUtils.isEmpty(positions)) {
                continue;
            }

            revaluePositions(entry.getKey(), positions, ltpCache);

            if (update == null) {
                update = new Update();
            }
            update.set(brokerField(entry.getKey()), positions);
        }

        return update;
    }

    private void revaluePositions(BrokerType broker, List<Holdings.HoldingInfo> positions,
                                  Map<String, Double> ltpCache) {
        var marginPolicy = brokerMarginPolicyFactory.getPolicy(broker);

        for (var position : positions) {
            var margin = marginService.getMarginCache().get(position.getSymbol());
            if (margin == null) {
                continue;
            }

            // Null means this broker will not fund the stock; leave the stored figure alone.
            var leverage = marginPolicy.leverageFor(margin);
            if (leverage != null) {
                position.setMargin(leverage.floatValue());
            }

            applyLtp(position, margin.getToken(), ltpCache);
        }
    }

    private void applyLtp(Holdings.HoldingInfo position, String token, Map<String, Double> ltpCache) {
        Double ltp = ltpCache.get(position.getSymbol());
        if (ltp == null) {
            ltp = fetchLtp(position.getSymbol(), token);
            if (ltp == null || ltp <= 0) {
                // Remember the failure so every other holder of this symbol skips the call.
                ltpCache.put(position.getSymbol(), 0d);
                return;
            }
        }

        if (ltp > 0) {
            position.setLtp(BigDecimal.valueOf(ltp));
            ltpCache.put(position.getSymbol(), ltp);
        }
    }

    private Double fetchLtp(String symbol, String token) {
        try {
            return marketDataQuery.getMarketTicker(token).getLtp();
        } catch (Exception e) {
            log.error("Error while getting ltp for symbol {}", symbol, e);
            return null;
        }
    }
}
