package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.service.OrderService;
import com.app.shahbaztrades.service.SessionManagerService;
import com.app.shahbaztrades.service.StrategyOrderService;
import com.app.shahbaztrades.service.ZerodhaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionManagerServiceImpl implements SessionManagerService {

    private final OrderService orderService;
    private final ZerodhaService zerodhaService;
    private final StrategyOrderService strategyOrderService;

    @Override
    @Async("taskExecutor")
    public void initiateZerodhaLogin() throws ExecutionException, InterruptedException {
       var orderFuture = CompletableFuture.supplyAsync(()->{
           var res = new HashSet<Long>();
           var orders = orderService.getTodayOrders();
           if (CollectionUtils.isEmpty(orders)){
               return res;
           }

           orders.forEach(order -> res.add(order.getUserId()));
           return res;
       });

        var strategyOrderFuture = CompletableFuture.supplyAsync(()->{
            var res = new HashSet<Long>();
            var orders = strategyOrderService.getTodayOrders();
            if (CollectionUtils.isEmpty(orders)){
                return res;
            }

            orders.forEach(order -> res.add(order.getUserId()));
            return res;
        });

        CompletableFuture.allOf(orderFuture,strategyOrderFuture).join();
        var userIds = orderFuture.get();
        userIds.addAll(strategyOrderFuture.get());

        zerodhaService.autoLogin(userIds);
    }
}
