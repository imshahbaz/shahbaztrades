package com.app.shahbaztrades.model.dto.angelone.websocket;

import java.time.ZonedDateTime;

public record LiveTick(double price, ZonedDateTime arrivalTime) {
}