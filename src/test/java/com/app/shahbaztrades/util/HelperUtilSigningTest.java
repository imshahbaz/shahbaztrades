package com.app.shahbaztrades.util;

import com.app.shahbaztrades.model.entity.Margin;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the security-sensitive and money-sensitive helpers in HelperUtil that
 * {@link HelperUtilTest} does not touch: OAuth state signing, cookie flags,
 * target pricing and the Rupeezy margin scraper.
 */
class HelperUtilSigningTest {

    private static final String KEY = "hmac-key-for-tests";

    @Test
    void signThenExtract_roundTripsTheUuid() {
        String signed = HelperUtil.signState("abc-123", KEY);
        assertEquals("abc-123", HelperUtil.extractAndVerify(signed, KEY));
    }

    @Test
    void extractAndVerify_rejectsATamperedPayload() {
        String signed = HelperUtil.signState("abc-123", KEY);
        String tampered = "attacker-uuid." + signed.split("\\.")[1];

        // The signature no longer covers the payload -> the session must be refused.
        assertNull(HelperUtil.extractAndVerify(tampered, KEY));
    }

    @Test
    void extractAndVerify_rejectsASignatureFromADifferentKey() {
        String signed = HelperUtil.signState("abc-123", "other-key");
        assertNull(HelperUtil.extractAndVerify(signed, KEY));
    }

    @Test
    void extractAndVerify_rejectsMalformedInput() {
        assertNull(HelperUtil.extractAndVerify(null, KEY));
        assertNull(HelperUtil.extractAndVerify("no-separator", KEY));
        assertNull(HelperUtil.extractAndVerify("a.b.c", KEY), "more than two segments is not a valid state");
        assertNull(HelperUtil.extractAndVerify("uuid.zzzz", KEY), "non-hex signature must not blow up");
    }

    @Test
    void createAuthCookie_isAlwaysHttpOnly() {
        assertTrue(HelperUtil.createAuthCookie("tok", 86400, false).contains("HttpOnly"));
        assertTrue(HelperUtil.createAuthCookie("tok", 86400, true).contains("HttpOnly"));
    }

    @Test
    void createAuthCookie_addsSecureAndSameSiteNoneOnlyInProduction() {
        String prod = HelperUtil.createAuthCookie("tok", 86400, true);
        assertTrue(prod.contains("Secure"));
        assertTrue(prod.contains("SameSite=None"), "cross-site auth needs SameSite=None in production");

        String local = HelperUtil.createAuthCookie("tok", 86400, false);
        assertFalse(local.contains("Secure"), "a Secure cookie would never be sent over plain http locally");
        assertFalse(local.contains("SameSite=None"));
    }

    @Test
    void createAuthCookie_logoutClearsTheValueAndDropsMaxAge() {
        String logout = HelperUtil.createAuthCookie("", -1, false);

        // A negative max-age is serialised as "no Max-Age", i.e. a session cookie with an empty value.
        assertTrue(logout.startsWith("auth_token=;"));
        assertFalse(logout.contains("Max-Age"));
    }

    @Test
    void dynamicTargetPrice_coversBrokerageTaxAndTheCapitalTarget() {
        // 10000 capital, entry 100, 100 shares.
        // target on capital = 70, + 47.2 fixed = 117.2 -> 1.172/share
        // tax = 100 * 0.00035 = 0.035
        // raw target = 101.207 -> tick 0.01 -> 101.21
        double target = HelperUtil.dynamicTargetPrice(new BigDecimal("10000"), new BigDecimal("100"), 100);
        assertEquals(101.21, target, 1e-9);
    }

    @Test
    void dynamicTargetPrice_isAlwaysAboveEntry() {
        // Even a single share must clear brokerage, so the exit can never be set below the entry.
        double target = HelperUtil.dynamicTargetPrice(new BigDecimal("1000"), new BigDecimal("500"), 1);
        assertTrue(target > 500.0);
    }

    @Test
    void dynamicTargetPrice_isSnappedToTheExchangeTick() {
        double target = HelperUtil.dynamicTargetPrice(new BigDecimal("50000"), new BigDecimal("1234.5"), 37);
        // Price band 1000-5000 trades in 0.10 ticks.
        assertEquals(0, Math.round(target * 100) % 10, "target must land on a valid 0.10 tick");
    }

    @Test
    void fixToTick_snapsAtTheBandBoundaries() {
        // <250 -> 0.01, 250 falls in the 0.05 band, >1000 -> 0.10, >5000 -> 0.50.
        assertEquals(249.99, HelperUtil.fixToTick(249.994), 1e-9);
        assertEquals(250.00, HelperUtil.fixToTick(250.02), 1e-9);
        assertEquals(1000.00, HelperUtil.fixToTick(1000.0), 1e-9);
        assertEquals(1000.10, HelperUtil.fixToTick(1000.06), 1e-9);
        assertEquals(5000.00, HelperUtil.fixToTick(5000.04), 1e-9);
        // >10000 -> 1.00 tick, >20000 -> 5.00 tick.
        assertEquals(15000.0, HelperUtil.fixToTick(15000.4), 1e-9);
        assertEquals(25000.0, HelperUtil.fixToTick(25001.0), 1e-9);
    }

    @Test
    void addRupeezyMargin_updatesOnlySymbolsAlreadyPresentInTheMap() {
        Map<String, Update> updates = new HashMap<>();
        updates.put("TCS", new Update());

        HelperUtil.addRupeezyMargin(updates, html("TCS", "4.42", "Tata Consultancy")
                + html("INFY", "3.10", "Infosys"));

        // TCS was requested, so it gains both fields; INFY was not in the map and must be ignored.
        String tcs = updates.get("TCS").getUpdateObject().toString();
        assertTrue(tcs.contains(Margin.Fields.rupeezyMargin));
        assertTrue(tcs.contains("Tata Consultancy"));
        assertNull(updates.get("INFY"));
    }

    @Test
    void addRupeezyMargin_skipsNonNumericLeverage() {
        Map<String, Update> updates = new HashMap<>();
        updates.put("TCS", new Update());

        HelperUtil.addRupeezyMargin(updates, html("TCS", "\"n/a\"", "Tata Consultancy"));

        // The name still lands, but a junk multiplier must not be written as a BigDecimal.
        String tcs = updates.get("TCS").getUpdateObject().toString();
        assertTrue(tcs.contains("Tata Consultancy"));
        assertFalse(tcs.contains(Margin.Fields.rupeezyMargin));
    }

    @Test
    void addRupeezyMargin_toleratesHtmlWithNoMatches() {
        Map<String, Update> updates = new HashMap<>();
        updates.put("TCS", new Update());

        HelperUtil.addRupeezyMargin(updates, "<html>no data here</html>");

        assertTrue(updates.get("TCS").getUpdateObject().isEmpty());
    }

    @Test
    void pollWait_returnsTrueWhenNotInterrupted() {
        assertTrue(HelperUtil.pollWait(1));
    }

    @Test
    void restTemplateAndExecutor_areInitialised() {
        // Static singletons other classes depend on; a null here fails the whole app at runtime.
        assertNotNull(HelperUtil.REST_TEMPLATE);
        assertNotNull(HelperUtil.EXECUTOR);
        assertNotNull(HelperUtil.ENCODER);
    }

    /** Builds one escaped JSON blob in the shape the Rupeezy page embeds and the regex expects. */
    private String html(String symbol, String leverage, String name) {
        return "{\\\"exchange\\\":\\\"NSE_EQ\\\",\\\"symbol\\\":\\\"" + symbol + "\\\","
                + "\\\"margin_multiplier\\\":" + leverage + ",\\\"security_desc\\\":\\\"" + name + "\\\","
                + "\\\"series\\\":\\\"EQ\\\"}";
    }
}
