package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.holdings.HoldingDto;
import com.app.shahbaztrades.model.entity.Holdings;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.repo.HoldingsRepo;
import com.app.shahbaztrades.service.HoldingsService;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.util.Constants;
import com.app.shahbaztrades.util.HelperUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingsServiceImpl implements HoldingsService {

    private final HoldingsRepo holdingsRepo;
    private final StringRedisTemplate stringRedisTemplate;
    private final MarginService marginService;
    private final MongoTemplate mongoTemplate;

    @Override
    public ResponseEntity<ApiResponse<List<HoldingDto>>> getAllHoldings(BrokerType brokerType, UserDto userDto) {
        var key = HOLDING_KEY + userDto.getUserId();
        var redisHoldings = stringRedisTemplate.opsForValue().get(key);
        Holdings holdings;
        if (!StringUtils.isEmpty(redisHoldings)) {
            holdings = HelperUtil.GSON.fromJson(redisHoldings, Holdings.class);
        } else {
            holdings = findHoldingsById(userDto.getUserId());
            stringRedisTemplate.opsForValue().set(key, HelperUtil.GSON.toJson(holdings));
        }

        var holdingInfo = holdings.getBrokerHoldingMap().get(brokerType);
        if (CollectionUtils.isEmpty(holdingInfo)) {
            throw new NotFoundException("Holdings not found");
        }

        return ResponseEntity.ok(ApiResponse.ok(holdingInfo.stream()
                .map(Holdings.HoldingInfo::toHoldingDto).toList(), "Holdings found"));
    }

    @Override
    public ResponseEntity<ApiResponse<Boolean>> createHoldings(BrokerType brokerType, UserDto userDto, HoldingDto holdingDto) {
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

        return ResponseEntity.ok(ApiResponse.ok(true, "Holdings added"));
    }

    @Override
    public ResponseEntity<ApiResponse<Boolean>> deleteHoldings(BrokerType brokerType, UserDto userDto, String symbol) {
        var criteria = Criteria.where(Holdings.Fields.userId).is(userDto.getUserId())
                .and(Holdings.Fields.brokerHoldingMap + Constants.DOT + brokerType.name()
                        + Constants.DOT + Holdings.HoldingInfo.Fields.symbol).is(symbol);

        var query = new Query(criteria);
        var update = new Update()
                .unset(Holdings.Fields.brokerHoldingMap + Constants.DOT + brokerType.name());

        var result = mongoTemplate.updateFirst(query, update, Holdings.class);
        if (result.getModifiedCount() > 0) {
            var key = HOLDING_KEY + userDto.getUserId();
            stringRedisTemplate.delete(key);
            return ResponseEntity.ok(ApiResponse.ok(true, "Holdings deleted"));
        }

        throw new NotFoundException("Holdings not found");
    }

    @Override
    public ResponseEntity<ApiResponse<Boolean>> updateHoldings(BrokerType brokerType, UserDto userDto, HoldingDto holdingDto) {
        var detail = holdingDto.getHoldingDetails().getFirst();
        if (detail.getId() <= 0) {
            throw new BadRequestException("Invalid Request");
        }

        var brokerPath = Holdings.Fields.brokerHoldingMap + Constants.DOT + brokerType.name();

        var query = Query.query(
                Criteria.where(Holdings.Fields.userId).is(userDto.getUserId())
                        .and(brokerPath + ".symbol").is(holdingDto.getSymbol())
        );

        var update = new Update()
                .set(
                        brokerPath + ".holdingDetails.$[detail]",
                        detail.toHoldingDetail()
                );

        update.filterArray(
                Criteria.where("detail.id").is(detail.getId())
        );

        var result = mongoTemplate.updateFirst(query, update, Holdings.class);
        if (result.getModifiedCount() > 0) {
            var key = HOLDING_KEY + userDto.getUserId();
            stringRedisTemplate.delete(key);
            return ResponseEntity.ok(ApiResponse.ok(true, "Holdings updated"));
        }

        throw new NotFoundException("Holdings not found");
    }

    private Holdings findHoldingsById(long userId) {
        return holdingsRepo.findById(userId)
                .orElseThrow(() -> new NotFoundException("Holdings not found"));
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
                        key -> new CopyOnWriteArrayList<>()
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

        var holdingInfo = Holdings.HoldingInfo.builder()
                .symbol(symbol)
                .margin(margin.getRequiredMargin())
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
