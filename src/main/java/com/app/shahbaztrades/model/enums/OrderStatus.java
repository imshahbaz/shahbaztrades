package com.app.shahbaztrades.model.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {

    PENDING("Pending", "#9E9E9E"),
    PLACED("Order Placed", "#2196F3"),
    BOUGHT("Bought", "#4CAF50"),
    STOP_LOSS_ACTIVE("Stop Loss Active", "#FF9800"),
    COMPLETED("Completed", "#009688"),
    REJECTED("Rejected", "#F44336"),
    FAILED("Failed", "#F44336");

    private final String label;
    private final String color;

    OrderStatus(String label, String color) {
        this.label = label;
        this.color = color;
    }
}
