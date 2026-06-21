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

    public static void validateSessionCallback(String header) {
        if (StringUtils.isEmpty(header) || !SESSION_MANAGER_SOURCE.equals(header)) {
            throw new UnauthorizedException("Unauthorized");
        }
    }
}
