package com.app.shahbaztrades.components.observer;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TickEvent {
    private String token;
    private double ltp;
}
