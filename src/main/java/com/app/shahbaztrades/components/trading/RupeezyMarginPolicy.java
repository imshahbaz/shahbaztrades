package com.app.shahbaztrades.components.trading;

import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.enums.BrokerType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Component
public class RupeezyMarginPolicy implements BrokerMarginPolicy {

    @Override
    public BrokerType getBrokerType() {
        return BrokerType.RUPEEZY;
    }

    @Override
    public BigDecimal leverageFor(Margin margin) {
        return margin.getRupeezyMargin();
    }

    /** Rupeezy leverage varies per stock, so the most leveraged candidate is tried first. */
    @Override
    public List<Margin> rankCandidates(List<Margin> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparing(Margin::getRupeezyMargin).reversed())
                .toList();
    }
}
