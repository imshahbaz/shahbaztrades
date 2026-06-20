package com.app.shahbaztrades.components.orderrouting;

import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.enums.BrokerType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OrderRouterFactory {

    private final Map<BrokerType, OrderRoutingStrategy> routerRegistry;

    public OrderRouterFactory(List<OrderRoutingStrategy> strategies) {
        this.routerRegistry = strategies.stream()
                .collect(Collectors.toMap(
                        OrderRoutingStrategy::getBrokerType,
                        Function.identity()
                ));
    }

    public OrderRoutingStrategy getRouter(BrokerType brokerType) {
        return Optional.ofNullable(routerRegistry.get(brokerType))
                .orElseThrow(() -> new NotFoundException(
                        "Order Execution Failed: Broker '" + brokerType + "' is not supported by this engine."
                ));
    }
}