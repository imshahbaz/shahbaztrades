package com.app.shahbaztrades.components.trading;

import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.enums.BrokerType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ZerodhaMarginPolicy implements BrokerMarginPolicy {

    @Override
    public BrokerType getBrokerType() {
        return BrokerType.ZERODHA;
    }

    @Override
    public BigDecimal leverageFor(Margin margin) {
        return margin.getRequiredMargin();
    }

    /** Zerodha's MTF leverage is flat across the list, so the screener's own order is kept. */
    @Override
    public List<Margin> rankCandidates(List<Margin> candidates) {
        return candidates;
    }
}
