package com.app.shahbaztrades.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RupeezyOrderType {
    REGULAR_LIMIT("RL"),
    REGULAR_MARKET("RL-MKT"),
    SL("SL"),
    SL_MARKET("SL-MKT");

    private final String type;
}