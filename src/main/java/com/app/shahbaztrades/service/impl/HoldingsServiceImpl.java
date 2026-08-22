package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.trading.BrokerMarginPolicyFactory;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.holdings.HoldingDto;
import com.app.shahbaztrades.model.entity.Holdings;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.repo.HoldingsRepo;
import com.app.shahbaztrades.repo.redis.HoldingsDataRedisRepo;
import com.app.shahbaztrades.service.MarketDataQuery;
import com.app.shahbaztrades.service.HoldingsService;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingsServiceImpl implements HoldingsService {

    private static final String HOLDINGS_NOT_FOUND = "Holdings not found";

    private final HoldingsRepo holdingsRepo;
    private final MarginService marginService;
    private final MongoTemplate mongoTemplate;
    private final MarketDataQuery marketDataQuery;
    private final BrokerMarginPolicyFactory brokerMarginPolicyFactory;
    private final HoldingsDataRedisRepo<Holdings> holdingsDataRedisRepo;

    @Override
    public List<HoldingDto> getAllHoldings(BrokerType brokerType, UserDto userDto) {
        Holdings holdings = holdingsDataRedisRepo.get(String.valueOf(userDto.getUserId()));
        if (holdings == null) {
            holdings = findHoldingsById(userDto.getUserId());
            holdingsDataRedisRepo.set(String.valueOf(userDto.getUserId()), holdings, Duration.ofMinutes(15));
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
        invalidateCache(userDto.getUserId());

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
            invalidateCache(userDto.getUserId());
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
        invalidateCache(userDto.getUserId());
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
        invalidateCache(userDto.getUserId());
        return true;
    }

    private void invalidateCache(long userId) {
        holdingsDataRedisRepo.delete(String.valueOf(userId));
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
                .orElseGet(() -> createHoldingInfo(holdingInfos, brokerType, symbol));
    }

    private Holdings.HoldingInfo createHoldingInfo(
            List<Holdings.HoldingInfo> holdingInfos,
            BrokerType brokerType,
            String symbol) {

        var margin = marginService.getMarginCache().get(symbol);
        if (margin == null) {
            throw new NotFoundException("Margin not found for " + symbol);
        }

        double ltp = 0;
        try {
            ltp = marketDataQuery.getMarketTicker(margin.getToken()).getLtp();
        } catch (Exception e) {
            log.error("Error while getting ltp for symbol {}", symbol, e);
        }

        var leverage = brokerMarginPolicyFactory.getPolicy(brokerType).leverageFor(margin);

        var holdingInfo = Holdings.HoldingInfo.builder()
                .symbol(symbol)
                .margin(leverage == null ? 0f : leverage.floatValue())
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
