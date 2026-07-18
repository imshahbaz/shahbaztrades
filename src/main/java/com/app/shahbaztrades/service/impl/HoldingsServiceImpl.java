package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.holdings.HoldingDto;
import com.app.shahbaztrades.model.entity.Holdings;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.repo.HoldingsRepo;
import com.app.shahbaztrades.service.AngelOneService;
import com.app.shahbaztrades.service.HoldingsService;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.util.Constants;
import com.app.shahbaztrades.util.HelperUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingsServiceImpl implements HoldingsService {

    private static final String HOLDINGS_NOT_FOUND = "Holdings not found";

    private final HoldingsRepo holdingsRepo;
    private final StringRedisTemplate stringRedisTemplate;
    private final MarginService marginService;
    private final MongoTemplate mongoTemplate;
    private final AngelOneService angelOneService;

    @Override
    public List<HoldingDto> getAllHoldings(BrokerType brokerType, UserDto userDto) {
        var key = HOLDING_KEY + userDto.getUserId();
        var redisHoldings = stringRedisTemplate.opsForValue().get(key);
        Holdings holdings;
        if (!StringUtils.isEmpty(redisHoldings)) {
            holdings = HelperUtil.GSON.fromJson(redisHoldings, Holdings.class);
        } else {
            holdings = findHoldingsById(userDto.getUserId());
            stringRedisTemplate.opsForValue().set(key, HelperUtil.GSON.toJson(holdings), Duration.ofMinutes(15));
        }

        var holdingInfo = holdings.getBrokerHoldingMap().get(brokerType);
        if (CollectionUtils.isEmpty(holdingInfo)) {
            throw new NotFoundException(HOLDINGS_NOT_FOUND);
        }

        return holdingInfo.stream()
                .map(Holdings.HoldingInfo::toHoldingDto).toList();
    }

    @Override
    public boolean createHoldings(BrokerType brokerType, UserDto userDto, HoldingDto holdingDto) {
        var holdings = getOrCreateHoldings(userDto.getUserId());

        var holdingInfo = getOrCreateHoldingInfo(
                holdings,
                brokerType,
                holdingDto.getSymbol()
        );

        var holdingDetails = buildHoldingDetails(holdingDto);

        assignIds(holdingInfo, holdingDetails);

        holdingInfo.getHoldingDetails().addAll(holdingDetails);

        holdingsRepo.save(holdings);
        var key = HOLDING_KEY + userDto.getUserId();
        stringRedisTemplate.delete(key);

        return true;
    }

    @Override
    public boolean deleteHoldings(BrokerType brokerType, UserDto userDto, String symbol) {
        var criteria = Criteria.where(Holdings.Fields.userId).is(userDto.getUserId())
                .and(Holdings.Fields.brokerHoldingMap + Constants.DOT + brokerType.name()
                        + Constants.DOT + Holdings.HoldingInfo.Fields.symbol).is(symbol);

        var query = new Query(criteria);
        var update = new Update()
                .pull(Holdings.Fields.brokerHoldingMap + Constants.DOT + brokerType.name(),
                        Query.query(Criteria.where(Holdings.HoldingInfo.Fields.symbol).is(symbol)));

        var result = mongoTemplate.updateFirst(query, update, Holdings.class);
        if (result.getModifiedCount() > 0) {
            var key = HOLDING_KEY + userDto.getUserId();
            stringRedisTemplate.delete(key);
            return true;
        }

        throw new NotFoundException(HOLDINGS_NOT_FOUND);
    }

    @Override
    public boolean updateHoldings(BrokerType brokerType, UserDto userDto, HoldingDto holdingDto) {
        var detail = holdingDto.getHoldingDetails().getFirst().toHoldingDetail();
        if (detail.getId() <= 0) {
            throw new BadRequestException("Invalid Request");
        }

        var holdings = findHoldingsById(userDto.getUserId());
        var holdingInfos = holdings.getBrokerHoldingMap().get(brokerType);
        if (CollectionUtils.isEmpty(holdingInfos)) {
            throw new BadRequestException(HOLDINGS_NOT_FOUND);
        }

        var info = holdingInfos.stream()
                .filter(i -> i.getSymbol().equals(holdingDto.getSymbol()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(HOLDINGS_NOT_FOUND));

        if (CollectionUtils.isEmpty(info.getHoldingDetails())) {
            throw new BadRequestException(HOLDINGS_NOT_FOUND);
        }

        var holdingDetail = info.getHoldingDetails().stream()
                .filter(det -> det.getId() == detail.getId())
                .findFirst()
                .orElseThrow(() -> new BadRequestException(HOLDINGS_NOT_FOUND));

        holdingDetail.setPrice(detail.getPrice());
        holdingDetail.setQuantity(detail.getQuantity());
        holdingDetail.setBuyDate(detail.getBuyDate());

        holdingsRepo.save(holdings);
        var key = HOLDING_KEY + userDto.getUserId();
        stringRedisTemplate.delete(key);
        return true;
    }

    @Override
    public boolean deleteHoldingDetail(BrokerType brokerType, UserDto userDto, String symbol, int id) {
        var holdings = findHoldingsById(userDto.getUserId());
        var holdingInfos = holdings.getBrokerHoldingMap().get(brokerType);
        if (CollectionUtils.isEmpty(holdingInfos)) {
            throw new BadRequestException(HOLDINGS_NOT_FOUND);
        }

        var info = holdingInfos.stream()
                .filter(i -> i.getSymbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(HOLDINGS_NOT_FOUND));

        if (CollectionUtils.isEmpty(info.getHoldingDetails())) {
            throw new BadRequestException(HOLDINGS_NOT_FOUND);
        }

        boolean removed = info.getHoldingDetails().removeIf(det -> det.getId() == id);
        if (!removed) {
            throw new BadRequestException(HOLDINGS_NOT_FOUND);
        }

        holdingsRepo.save(holdings);
        var key = HOLDING_KEY + userDto.getUserId();
        stringRedisTemplate.delete(key);
        return true;
    }

    @Override
    @Async("taskExecutor")
    public void updatePortfolio() {
        var zerodhaField = Holdings.Fields.brokerHoldingMap + Constants.DOT + BrokerType.ZERODHA.name();

        // Only load users who actually hold Zerodha positions, instead of the whole collection.
        var holdings = mongoTemplate.find(new Query(Criteria.where(zerodhaField).exists(true)), Holdings.class);
        if (CollectionUtils.isEmpty(holdings)) {
            return;
        }

        Map<String, Double> ltpMap = new HashMap<>();
        var keys = new ArrayList<String>(holdings.size());
        var bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Holdings.class);
        boolean hasUpdates = false;

        for (var info : holdings) {
            var zerodhaHoldings = info.getBrokerHoldingMap().get(BrokerType.ZERODHA);
            if (CollectionUtils.isEmpty(zerodhaHoldings)) {
                continue;
            }

            processZerodhaHoldings(zerodhaHoldings, ltpMap);

            // Update only the Zerodha sub-array for this user, not the entire document.
            bulkOps.updateOne(
                    new Query(Criteria.where(Constants.MONGO_ID).is(info.getUserId())),
                    new Update().set(zerodhaField, zerodhaHoldings));
            hasUpdates = true;
            keys.add(HOLDING_KEY + info.getUserId());
        }

        if (hasUpdates) {
            bulkOps.execute();
            stringRedisTemplate.delete(keys);
        }
    }

    private void processZerodhaHoldings(List<Holdings.HoldingInfo> zerodhaHoldings, Map<String, Double> ltpMap) {
        for (var det : zerodhaHoldings) {
            var margin = marginService.getMarginCache().get(det.getSymbol());
            if (margin == null) {
                continue;
            }

            det.setMargin(margin.getRequiredMargin().floatValue());
            updateLtpForHolding(det, margin.getToken(), ltpMap);
        }
    }

    private void updateLtpForHolding(Holdings.HoldingInfo det, String token, Map<String, Double> ltpMap) {
        Double ltp = ltpMap.get(det.getSymbol());
        if (ltp == null) {
            ltp = fetchLtpFromAngelOne(det.getSymbol(), token);
            if (ltp == null || ltp <= 0) {
                ltpMap.put(det.getSymbol(), 0d);
                return;
            }
        }

        if (ltp > 0) {
            det.setLtp(BigDecimal.valueOf(ltp));
            ltpMap.put(det.getSymbol(), ltp);
        }
    }

    private Double fetchLtpFromAngelOne(String symbol, String token) {
        try {
            return angelOneService.getMarketTicker(token).ltp();
        } catch (Exception e) {
            log.error("Error while getting ltp for symbol {}", symbol, e);
            return null;
        }
    }

    private Holdings findHoldingsById(long userId) {
        return holdingsRepo.findById(userId)
                .orElseThrow(() -> new NotFoundException(HOLDINGS_NOT_FOUND));
    }

    private Holdings getOrCreateHoldings(long userId) {
        return holdingsRepo.findById(userId)
                .orElseGet(() -> Holdings.builder()
                        .userId(userId)
                        .build());
    }

    private List<Holdings.HoldingDetail> buildHoldingDetails(HoldingDto holdingDto) {
        return holdingDto.getHoldingDetails().stream()
                .map(HoldingDto.HoldingDetailDto::toHoldingDetail)
                .collect(Collectors.toCollection(CopyOnWriteArrayList::new));
    }

    private Holdings.HoldingInfo getOrCreateHoldingInfo(
            Holdings holdings,
            BrokerType brokerType,
            String symbol) {

        var holdingInfos = holdings.getBrokerHoldingMap()
                .computeIfAbsent(
                        brokerType,
                        _ -> new CopyOnWriteArrayList<>()
                );

        return holdingInfos.stream()
                .filter(info -> info.getSymbol().equals(symbol))
                .findFirst()
                .orElseGet(() -> createHoldingInfo(
                        holdingInfos,
                        symbol
                ));
    }

    private Holdings.HoldingInfo createHoldingInfo(
            List<Holdings.HoldingInfo> holdingInfos,
            String symbol) {

        var margin = marginService.getMarginCache().get(symbol);

        double ltp = 0;
        try {
            ltp = angelOneService.getMarketTicker(margin.getToken()).ltp();
        } catch (Exception e) {
            log.error("Error while getting ltp for symbol {}", symbol, e);
        }

        var holdingInfo = Holdings.HoldingInfo.builder()
                .symbol(symbol)
                .margin(margin.getRequiredMargin().floatValue())
                .ltp(BigDecimal.valueOf(ltp))
                .build();

        holdingInfos.add(holdingInfo);

        return holdingInfo;
    }

    private void assignIds(
            Holdings.HoldingInfo holdingInfo,
            List<Holdings.HoldingDetail> holdingDetails) {

        int nextId = holdingInfo.getHoldingDetails().isEmpty()
                ? 1
                : holdingInfo.getHoldingDetails().getLast().getId() + 1;

        for (var detail : holdingDetails) {
            detail.setId(nextId++);
        }
    }
}
