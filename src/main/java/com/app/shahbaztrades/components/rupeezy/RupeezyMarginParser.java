package com.app.shahbaztrades.components.rupeezy;

import com.app.shahbaztrades.model.entity.Margin;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes leverage out of Rupeezy's margin page. Rupeezy publishes no API for this, so the figures
 * are read from embedded JSON in the HTML — hence the pattern rather than a parser.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RupeezyMarginParser {

    private static final Pattern MARGIN_PATTERN =
            Pattern.compile("\\{\\\\\"exchange\\\\\":\\\\\"NSE_EQ\\\\\".*?\\\\\"series\\\\\":\\\\\"EQ\\\\\"}");

    /** Adds name and leverage to the pending update for each symbol already present in the map. */
    public static void addRupeezyMargin(Map<String, Update> map, String html) {
        Matcher matcher = MARGIN_PATTERN.matcher(html);

        while (matcher.find()) {
            String json = matcher.group()
                    .replace("\\\"", "\"")
                    .replace("\\u0026", "&");

            String symbol = extract(json, "\"symbol\":\"", "\"");
            var update = map.get(symbol);
            if (!StringUtils.isEmpty(symbol) && update != null) {
                String leverage = extract(json, "\"margin_multiplier\":", ",");
                String stockName = extract(json, "\"security_desc\":\"", "\"");
                if (!StringUtils.isEmpty(stockName)) {
                    update.set(Margin.Fields.name, stockName);
                }

                if (NumberUtils.isCreatable(leverage)) {
                    update.set(Margin.Fields.rupeezyMargin, new BigDecimal(leverage));
                }
            }
        }
    }

    private static String extract(String text, String start, String end) {
        int s = text.indexOf(start);
        if (s == -1) return "";

        s += start.length();
        int e = text.indexOf(end, s);

        if (e == -1) return text.substring(s);
        return text.substring(s, e);
    }
}
