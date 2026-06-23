package com.app.shahbaztrades.components.helper;

import com.app.shahbaztrades.model.dto.fcm.NotificationRequest;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.repo.OrderRepo;
import com.app.shahbaztrades.service.FcmService;
import com.app.shahbaztrades.service.UserService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;

@Component
@RequiredArgsConstructor
public class AsyncHelper {

    private final OrderRepo orderRepo;
    private final UserService userService;
    private final FcmService fcmService;

    @EventListener
    @Async("taskExecutor")
    public void handleOrder(Order order) {
        orderRepo.save(order);
    }

    @EventListener
    @Async("taskExecutor")
    public void handleNotificationRequest(NotificationRequest request) {
        var user = userService.findByUserIdOrEmailOrMobile(request.userId(), null, null);
        if (user == null || StringUtils.isEmpty(user.getFcmToken())) {
            return;
        }

        fcmService.sendNotification(user.getFcmToken(), request.title(), request.body(), CollectionUtils.isEmpty(request.data()) ? new HashMap<>() : new HashMap<>(request.data()));
    }

}
