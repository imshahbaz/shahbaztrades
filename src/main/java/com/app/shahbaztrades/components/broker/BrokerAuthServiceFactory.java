package com.app.shahbaztrades.components.broker;

import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.service.BrokerAuthService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class BrokerAuthServiceFactory {

    private final Map<BrokerType, BrokerAuthService> registry;

    public BrokerAuthServiceFactory(List<BrokerAuthService> services) {
        this.registry = services.stream()
                .collect(Collectors.toMap(BrokerAuthService::getBrokerType, Function.identity()));
    }

    public BrokerAuthService forBroker(BrokerType brokerType) {
        return Optional.ofNullable(registry.get(brokerType))
                .orElseThrow(() -> new NotFoundException("No auth service registered for broker " + brokerType));
    }
}
