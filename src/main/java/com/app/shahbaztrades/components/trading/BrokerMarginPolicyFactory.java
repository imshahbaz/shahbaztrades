package com.app.shahbaztrades.components.trading;

import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.enums.BrokerType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class BrokerMarginPolicyFactory {

    private final Map<BrokerType, BrokerMarginPolicy> policyRegistry;

    public BrokerMarginPolicyFactory(List<BrokerMarginPolicy> policies) {
        this.policyRegistry = policies.stream()
                .collect(Collectors.toMap(BrokerMarginPolicy::getBrokerType, Function.identity()));
    }

    public BrokerMarginPolicy getPolicy(BrokerType brokerType) {
        return Optional.ofNullable(policyRegistry.get(brokerType))
                .orElseThrow(() -> new NotFoundException(
                        "Sizing Failed: Broker '" + brokerType + "' has no margin policy configured."));
    }
}
