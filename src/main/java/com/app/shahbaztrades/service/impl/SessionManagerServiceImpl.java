package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.broker.BrokerAuthServiceFactory;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.fcm.NotificationRequest;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.service.*;
import com.app.shahbaztrades.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionManagerServiceImpl implements SessionManagerService {

    private final OrderService orderService;
    private final ZerodhaAutoLoginService zerodhaAutoLoginService;
    private final BrokerAuthServiceFactory brokerAuthServiceFactory;
    private final StrategyOrderService strategyOrderService;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserService userService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final AsyncTaskExecutor taskExecutor;

    @Override
    @Async("taskExecutor")
    public void initiateZerodhaLogin() throws ExecutionException, InterruptedException {
        Set<Long> usersToRemind = ConcurrentHashMap.newKeySet();
        var orderFuture = CompletableFuture.supplyAsync(() -> {
            var res = new HashSet<Long>();
            var orders = orderService.getTodayOrders();
            if (CollectionUtils.isEmpty(orders)) {
                return res;
            }

            orders.forEach(order -> partition(order.getBroker(), order.getUserId(), res, usersToRemind));

            return res;
        }, taskExecutor);

        var strategyOrderFuture = CompletableFuture.supplyAsync(() -> {
            var res = new HashSet<Long>();
            var orders = strategyOrderService.getTodayOrders();
            if (CollectionUtils.isEmpty(orders)) {
                return res;
            }

            orders.forEach(order -> partition(order.getBroker(), order.getUserId(), res, usersToRemind));

            return res;
        }, taskExecutor);

        CompletableFuture.allOf(orderFuture, strategyOrderFuture).join();
        var userIds = orderFuture.get();
        userIds.addAll(strategyOrderFuture.get());

        usersToRemind.forEach(userId -> applicationEventPublisher.publishEvent(NotificationRequest.builder()
                .userId(userId)
                .title(Constants.NOTIFICATION_TITLE_BROKER_LOGIN)
                .body(Constants.NOTIFICATION_MESSAGE_BROKER_LOGIN)
                .data(Collections.emptyMap())
                .build()));

        zerodhaAutoLoginService.autoLogin(userIds);
    }

    @Override
    public boolean autoConnectZerodhaSession(UserDto userDto) {
        Boolean isAbsent = stringRedisTemplate.opsForValue().setIfAbsent(
                Constants.ZERODHA_AUTO_LOGIN_KEY + userDto.getUserId(),
                "PENDING",
                Duration.ofMinutes(3)
        );

        if (!Boolean.TRUE.equals(isAbsent)) {
            throw new ResourceAlreadyExistsException("Request already exists");
        }

        zerodhaAutoLoginService.autoConnectZerodhaSession(userService.findByUserIdOrEmailOrMobile(userDto.getUserId(), "", 0L));
        return true;
    }
    /** Brokers we can log in for go to auto-login; the rest have to be nudged to do it themselves. */
    private void partition(BrokerType broker, long userId, Set<Long> autoLogin, Set<Long> toRemind) {
        if (brokerAuthServiceFactory.forBroker(broker).supportsAutoLogin()) {
            autoLogin.add(userId);
        } else {
            toRemind.add(userId);
        }
    }

}
