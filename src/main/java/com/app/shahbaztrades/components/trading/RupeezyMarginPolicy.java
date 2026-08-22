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

    /**
     * Rupeezy leverage varies per stock, so the most leveraged candidate is tried first.
     * <p>
     * Stocks Rupeezy will not fund carry a null margin; they sort last and are then skipped by the
     * sizing step. Ranking must tolerate them rather than throw, because the caller turns any
     * exception into "no trade" — so one unfunded stock would otherwise discard the entire signal,
     * funded candidates included.
     * <p>
     * Note the null handling belongs inside the key comparator: {@code .reversed()} applied to the
     * whole comparator would flip {@code nullsLast} into {@code nullsFirst} and try the unfunded
     * stocks first.
     */
    @Override
    public List<Margin> rankCandidates(List<Margin> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparing(Margin::getRupeezyMargin,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}
