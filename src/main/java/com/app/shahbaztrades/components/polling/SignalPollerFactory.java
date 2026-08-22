package com.app.shahbaztrades.components.polling;

import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.enums.PollerType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SignalPollerFactory {

    private final Map<PollerType, SignalPoller> pollerRegistry;

    public SignalPollerFactory(List<SignalPoller> pollers) {
        this.pollerRegistry = pollers.stream()
                .collect(Collectors.toMap(SignalPoller::getType, Function.identity()));
    }

    public SignalPoller getPoller(PollerType type) {
        return Optional.ofNullable(pollerRegistry.get(type))
                .orElseThrow(() -> new NotFoundException("No signal poller registered for " + type));
    }
}
