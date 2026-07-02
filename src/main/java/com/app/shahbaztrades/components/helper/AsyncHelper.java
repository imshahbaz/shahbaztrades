package com.app.shahbaztrades.components.helper;

import com.app.shahbaztrades.model.dto.fcm.NotificationRequest;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.repo.OrderRepo;
import com.app.shahbaztrades.service.FcmService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AsyncHelper {

    private final OrderRepo orderRepo;
    private final FcmService fcmService;

    @EventListener
    @Async("taskExecutor")
    public void handleOrder(Order order) {
        orderRepo.save(order);
    }

    @EventListener
    @Async("taskExecutor")
    public void handleNotificationRequest(NotificationRequest request) {
        fcmService.sendNotification(request.userId(), request.title(), request.body(), request.data());
    }

}
