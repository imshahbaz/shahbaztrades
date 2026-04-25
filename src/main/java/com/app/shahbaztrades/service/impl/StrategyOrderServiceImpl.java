package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.order.StrategyOrderDto;
import com.app.shahbaztrades.model.entity.StrategyOrder;
import com.app.shahbaztrades.repo.StrategyOrderRepo;
import com.app.shahbaztrades.service.StrategyOrderService;
import com.app.shahbaztrades.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StrategyOrderServiceImpl implements StrategyOrderService {

    private final MongoTemplate mongoTemplate;
    private final StrategyOrderRepo strategyOrderRepo;

    @Override
    public List<StrategyOrderDto> getAllOrdersAdmin(String strategyName) {
        Query query = new Query(Criteria.where(StrategyOrder.Fields.strategyName).is(strategyName));
        query.with(Sort.by(Sort.Direction.DESC, StrategyOrder.Fields.date));
        var orders = mongoTemplate.find(query, StrategyOrder.class);
        return orders.stream().map(StrategyOrder::toDto).toList();
    }

    @Override
    public StrategyOrderDto getOrderById(String orderId) {
        var order = strategyOrderRepo.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Strategy order not found"));
        return order.toDto();
    }

    @Override
    public StrategyOrderDto createOrder(StrategyOrderDto request) {
        var entity = request.toEntity();
        try {
            entity = strategyOrderRepo.insert(entity);
        } catch (DataIntegrityViolationException e) {
            throw new ResourceAlreadyExistsException("Strategy order already exists for this user on this date");
        }
        return entity.toDto();
    }

    @Override
    public StrategyOrderDto updateOrder(StrategyOrderDto request) {
        var entity = request.toEntity();
        try {
            entity = strategyOrderRepo.save(entity);
        } catch (DataIntegrityViolationException e) {
            throw new ResourceAlreadyExistsException("Strategy order already exists for this user on this date");
        }
        return entity.toDto();
    }

    @Override
    public void deleteOrder(String id) {
        strategyOrderRepo.deleteById(id);
    }

    @Override
    public List<StrategyOrderDto> getOrdersByUserId(long userId) {
        Query query = new Query(Criteria.where(StrategyOrder.Fields.userId).is(userId));
        query.with(Sort.by(Sort.Direction.DESC, StrategyOrder.Fields.date));
        var orders = mongoTemplate.find(query, StrategyOrder.class);
        return orders.stream().map(StrategyOrder::toDto).toList();
    }

    @Override
    public List<StrategyOrder> getTodayOrders() {
        var today = DateUtil.getTodayDate();
        Query query = Query.query(Criteria.where(StrategyOrder.Fields.date).gte(today.atStartOfDay())
                .and(StrategyOrder.Fields.date).lt(today.plusDays(1).atStartOfDay()));
        return mongoTemplate.find(query, StrategyOrder.class);
    }

}
