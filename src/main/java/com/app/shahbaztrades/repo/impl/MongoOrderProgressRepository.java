package com.app.shahbaztrades.repo.impl;

import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.repo.OrderProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoOrderProgressRepository implements OrderProgressRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public void saveProgress(Order order) {
        Query query = Query.query(Criteria.where(Order.Fields.id).is(order.getId()));
        // A targeted update, so a concurrent write to any other field is not clobbered.
        Update update = new Update()
                .set(Order.Fields.entry, order.getEntry())
                .set(Order.Fields.exit, order.getExit())
                .set(Order.Fields.atr, order.getAtr())
                .set(Order.Fields.orderStatus, order.getOrderStatus());

        try {
            mongoTemplate.updateFirst(query, update, Order.class);
        } catch (Exception e) {
            log.error("Error updating order status {} updates {}", order.getId(), update);
        }
    }
}
