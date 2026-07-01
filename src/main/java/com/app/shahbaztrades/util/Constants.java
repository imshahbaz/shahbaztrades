package com.app.shahbaztrades.util;

import com.app.shahbaztrades.exceptions.UnauthorizedException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Constants {
    public static final String ZERODHA_AUTO_LOGIN_KEY = "ZERODHA_AUTO_LOGIN:";
    public static final String SESSION_MANAGER_SOURCE = "session-manager";
    public static final String DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    public static final Pattern RUPEEZY_MARGIN_PATTERN = Pattern.compile("\\{\\\\\"exchange\\\\\":\\\\\"NSE_EQ\\\\\".*?\\\\\"series\\\\\":\\\\\"EQ\\\\\"}");
    public static final String NOTIFICATION_TITLE_PLACED = "Order Placed Successfully";
    public static final String NOTIFICATION_TITLE_BUY = "Buy Order Executed";
    public static final String NOTIFICATION_TITLE_SELL = "Sell Order Executed";
    public static final String NOTIFICATION_MESSAGE_PLACED = "Your order for %d shares of %s has been submitted successfully.";
    public static final String NOTIFICATION_MESSAGE_BUY = "Bought %d shares of %s at ₹%.2f.";
    public static final String NOTIFICATION_MESSAGE_SELL = "Sold %d shares of %s at ₹%.2f.";
    public static final String NOTIFICATION_MESSAGE_SELL_MARKET = "Your market sell order for %d shares of %s has been submitted successfully.";
    public static final String NOTIFICATION_MESSAGE_SELL_SL = "Your stop-loss sell order for %d shares of %s at ₹%.2f has been submitted successfully.";

    public static final String DOT = ".";
    public static final String MONGO_ID = "_id";

    public static void validateSessionCallback(String header) {
        if (StringUtils.isEmpty(header) || !SESSION_MANAGER_SOURCE.equals(header)) {
            throw new UnauthorizedException("Unauthorized");
        }
    }

}
