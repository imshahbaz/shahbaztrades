package com.app.shahbaztrades.util;

import com.app.shahbaztrades.exceptions.UnauthorizedException;
import org.apache.commons.lang3.StringUtils;

public class Constants {
    public static final String ZERODHA_AUTO_LOGIN_KEY = "ZERODHA_AUTO_LOGIN:";
    public static final String SESSION_MANAGER_SOURCE = "session-manager";

    public static void validateSessionCallback(String header) {
        if (StringUtils.isEmpty(header) || !SESSION_MANAGER_SOURCE.equals(header)) {
            throw new UnauthorizedException("Unauthorized");
        }
    }
}
