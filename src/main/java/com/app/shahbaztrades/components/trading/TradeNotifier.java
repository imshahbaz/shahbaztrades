package com.app.shahbaztrades.components.trading;

import com.app.shahbaztrades.model.dto.fcm.NotificationRequest;
import com.app.shahbaztrades.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Builds the user-facing messages for trading. Keeps message wording out of the trading logic,
 * which otherwise has to fully qualify {@code Constants} to dodge the KiteConnect class of the
 * same name.
 */
@RequiredArgsConstructor
@Component
public class TradeNotifier {

    private final ApplicationEventPublisher eventPublisher;

    public void buyExecuted(long userId, int quantity, String symbol, double price) {
        publish(userId, Constants.NOTIFICATION_TITLE_BUY,
                String.format(Constants.NOTIFICATION_MESSAGE_BUY, quantity, symbol, price));
    }

    public void sellExecuted(long userId, int quantity, String symbol, double price) {
        publish(userId, Constants.NOTIFICATION_TITLE_SELL,
                String.format(Constants.NOTIFICATION_MESSAGE_SELL, quantity, symbol, price));
    }

    public void marketSellPlaced(long userId, int quantity, String symbol) {
        publish(userId, Constants.NOTIFICATION_TITLE_PLACED,
                String.format(Constants.NOTIFICATION_MESSAGE_SELL_MARKET, quantity, symbol));
    }

    public void stopLossPlaced(long userId, int quantity, String symbol, double price) {
        publish(userId, Constants.NOTIFICATION_TITLE_PLACED,
                String.format(Constants.NOTIFICATION_MESSAGE_SELL_SL, quantity, symbol, price));
    }

    /** Entry filled but the exit could not be placed: the user has an unprotected position. */
    public void orphanedPosition(long userId, int quantity, String symbol) {
        publish(userId, Constants.NOTIFICATION_TITLE_BUY,
                String.format(Constants.NOTIFICATION_MESSAGE_ORPHANED_POSITION, quantity, symbol));
    }

    private void publish(long userId, String title, String body) {
        eventPublisher.publishEvent(new NotificationRequest(userId, title, body, Collections.emptyMap()));
    }
}
