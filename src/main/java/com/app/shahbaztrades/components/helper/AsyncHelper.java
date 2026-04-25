package com.app.shahbaztrades.components.helper;

import com.app.shahbaztrades.model.dto.fcm.NotificationRequest;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.repo.OrderRepo;
import com.app.shahbaztrades.service.FcmService;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.util.HelperUtil;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class AsyncHelper {

    private final EventBus eventBus;
    private final OrderRepo orderRepo;
    private final UserService userService;
    private final FcmService fcmService;

    public AsyncHelper(OrderRepo orderRepo, UserService userService, FcmService fcmService) {
        this.orderRepo = orderRepo;
        this.userService = userService;
        this.fcmService = fcmService;
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

    @Subscribe
    public void handleNotificationRequest(NotificationRequest request) {
        var user = userService.findByUserIdOrEmailOrMobile(request.userId(), null, null);
        if (user == null || StringUtils.isEmpty(user.getFcmToken())) {
            return;
        }

        fcmService.sendNotification(user.getFcmToken(), request.title(), request.body(), request.data());
    }

}
