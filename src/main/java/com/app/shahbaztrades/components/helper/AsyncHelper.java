package com.app.shahbaztrades.components.helper;

import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.repo.OrderRepo;
import com.app.shahbaztrades.util.HelperUtil;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.springframework.stereotype.Component;

@Component
public class AsyncHelper {

    private final EventBus eventBus;
    private final OrderRepo orderRepo;

    public AsyncHelper(OrderRepo orderRepo) {
        this.orderRepo = orderRepo;
        this.eventBus = new AsyncEventBus(HelperUtil.EXECUTOR);
        eventBus.register(this);
    }

    public void post(Object event) {
        this.eventBus.post(event);
    }

    @Subscribe
    public void handleOrder(Order order) {
        orderRepo.save(order);
    }

}
