package com.app.shahbaztrades.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ExchangeType {
    NSE(1),
    NFO(2),
    BFO(4);

    private final int value;
}