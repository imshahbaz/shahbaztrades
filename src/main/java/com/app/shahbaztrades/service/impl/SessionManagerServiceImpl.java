package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.service.*;
import com.app.shahbaztrades.util.Constants;
import com.app.shahbaztrades.util.HelperUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionManagerServiceImpl implements SessionManagerService {

    private final OrderService orderService;
    private final ZerodhaService zerodhaService;
    private final StrategyOrderService strategyOrderService;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserService userService;

    @Override
    @Async("taskExecutor")
    public void initiateZerodhaLogin() throws ExecutionException, InterruptedException {
        var orderFuture = CompletableFuture.supplyAsync(() -> {
            var res = new HashSet<Long>();
            var orders = orderService.getTodayOrders();
            if (CollectionUtils.isEmpty(orders)) {
                return res;
            }

            orders.forEach(order -> {
                if (order.getBroker().equals(BrokerType.ZERODHA)) {
                    res.add(order.getUserId());
                }
            });

            return res;
        }, HelperUtil.EXECUTOR);

        var strategyOrderFuture = CompletableFuture.supplyAsync(() -> {
            var res = new HashSet<Long>();
            var orders = strategyOrderService.getTodayOrders();
            if (CollectionUtils.isEmpty(orders)) {
                return res;
            }

            orders.forEach(order -> {
                if (order.getBroker().equals(BrokerType.ZERODHA)) {
                    res.add(order.getUserId());
                }
            });

            return res;
        }, HelperUtil.EXECUTOR);

        CompletableFuture.allOf(orderFuture, strategyOrderFuture).join();
        var userIds = orderFuture.get();
        userIds.addAll(strategyOrderFuture.get());

        zerodhaService.autoLogin(userIds);
    }

    @Override
    public boolean autoConnectZerodhaSession(UserDto userDto) {
        Boolean isAbsent = stringRedisTemplate.opsForValue().setIfAbsent(
                Constants.ZERODHA_AUTO_LOGIN_KEY + userDto.getUserId(),
                "PENDING",
                3,
                TimeUnit.MINUTES
        );

        if (!Boolean.TRUE.equals(isAbsent)) {
            throw new ResourceAlreadyExistsException("Request already exists");
        }

        zerodhaService.autoConnectZerodhaSession(userService.findByUserIdOrEmailOrMobile(userDto.getUserId(), "", 0L));
        return true;
    }
}
