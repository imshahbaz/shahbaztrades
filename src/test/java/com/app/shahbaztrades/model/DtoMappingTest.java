package com.app.shahbaztrades.model;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.AuthCallbackResponse;
import com.app.shahbaztrades.model.dto.brevo.BrevoEmailRequest;
import com.app.shahbaztrades.model.dto.kronos.KronosPredictionResponse;
import com.app.shahbaztrades.model.dto.kronos.PredictionItemDto;
import com.app.shahbaztrades.model.dto.nse.NSEHistoricalData;
import com.app.shahbaztrades.model.dto.order.StrategyOrderDto;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import com.app.shahbaztrades.model.dto.strategy.TargetStockResult;
import com.app.shahbaztrades.model.entity.KronosPredictions;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.entity.Strategy;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.model.enums.OtpFor;
import com.app.shahbaztrades.model.enums.TimeFrame;
import com.app.shahbaztrades.util.DateUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DtoMappingTest {

    // --- ApiResponse ------------------------------------------------------

    @Test
    void apiResponse_okCarriesDataAndMessageWithoutAnError() {
        ApiResponse<String> response = ApiResponse.ok("payload", "done");

        assertTrue(response.isSuccess());
        assertEquals("payload", response.getData());
        assertEquals("done", response.getMessage());
        assertNull(response.getError());
    }

    @Test
    void apiResponse_failCarriesTheErrorWithoutData() {
        ApiResponse<String> response = ApiResponse.fail("boom");

        assertFalse(response.isSuccess());
        assertEquals("boom", response.getError());
        assertNull(response.getData());
    }

    // --- Strategy ---------------------------------------------------------

    @Test
    void strategyDto_toEntityCanonicalisesTheNameToUpperCase() {
        // The cache is keyed by upper-case name, so persistence must agree.
        StrategyDto dto = StrategyDto.builder()
                .name("myStrat").scanClause("close > 100").active(true).timeFrame(TimeFrame.DAILY).build();

        Strategy entity = dto.toEntity();

        assertEquals("MYSTRAT", entity.getName());
        assertEquals("MYSTRAT", dto.getName(), "the dto is normalised in place too");
        assertEquals("close > 100", entity.getScanClause());
        assertEquals(TimeFrame.DAILY, entity.getTimeFrame());
    }

    @Test
    void strategy_toDtoCarriesTheSuccessRate() {
        StrategyDto dto = Strategy.builder()
                .name("S").scanClause("c").active(true).successRate(62.5f).timeFrame(TimeFrame.FIFTEEN_MINUTE)
                .build().toDto();

        assertEquals(62.5f, dto.getSuccessRate());
        assertTrue(dto.isActive());
    }

    // --- StrategyOrder ----------------------------------------------------

    @Test
    void strategyOrderDto_toEntityPinsTheDateToIstMidnight() {
        String tomorrow = DateUtil.getTodayDate().plusDays(1).toString();

        var entity = StrategyOrderDto.builder()
                .id("s1").userId(4L).strategyName("RSI15MIN").date(tomorrow)
                .amount(new BigDecimal("10000")).broker(BrokerType.RUPEEZY)
                .build().toEntity();

        assertEquals(LocalDate.parse(tomorrow).atStartOfDay(DateUtil.IST_ZONE).toInstant(), entity.getDate());
        assertEquals(BrokerType.RUPEEZY, entity.getBroker());
        assertEquals(tomorrow, entity.toDto().getDate(), "round trip must return the same ISO day");
    }

    @Test
    void strategyOrderDto_toEntityRejectsAnInvalidOrExpiredDate() {
        assertThrows(BadRequestException.class, () -> StrategyOrderDto.builder()
                .strategyName("S").date("tomorrow").amount(BigDecimal.ONE).broker(BrokerType.ZERODHA)
                .build().toEntity());
        assertThrows(BadRequestException.class, () -> StrategyOrderDto.builder()
                .strategyName("S").date("2000-01-01").amount(BigDecimal.ONE).broker(BrokerType.ZERODHA)
                .build().toEntity());
    }

    // --- Kronos -----------------------------------------------------------

    @Test
    void predictionItem_splitsIntoAHeaderAndACandle() {
        PredictionItemDto item = PredictionItemDto.builder()
                .symbol("TCS")
                .runDate(LocalDate.of(2026, 8, 15))
                .contextEndDate(LocalDate.of(2026, 8, 14))
                .date(LocalDate.of(2026, 8, 16))
                .horizonDay(1)
                .anchorClose(new BigDecimal("3200"))
                .open(new BigDecimal("3210")).high(new BigDecimal("3250"))
                .low(new BigDecimal("3190")).close(new BigDecimal("3240"))
                .paths(100)
                .build();

        KronosPredictions header = item.mapKronosPredictions();
        KronosPredictions.PredictedCandle candle = item.mapPredictedCandle();

        assertEquals("TCS", header.getSymbol());
        assertEquals("15-Aug-2026", header.getRunDate());
        assertEquals("14-Aug-2026", header.getContextEndDate());
        assertEquals(100, header.getPaths());
        assertEquals("16-Aug-2026", candle.getDate());
        assertEquals(1, candle.getHorizonDay());
    }

    @Test
    void predictionItem_toleratesMissingDates() {
        var item = PredictionItemDto.builder().symbol("TCS").build();

        assertNull(item.mapKronosPredictions().getRunDate());
        assertNull(item.mapPredictedCandle().getDate());
    }

    @Test
    void kronosResponse_ordersPredictedCandlesByHorizonDay() {
        // Mongo returns sub-documents in insertion order; the chart needs them chronological.
        var predictions = KronosPredictions.builder()
                .symbol("TCS").runDate("15-Aug-2026").contextEndDate("14-Aug-2026")
                .predictedCandles(new java.util.ArrayList<>(List.of(
                        candle(3, "18-Aug-2026"), candle(1, "16-Aug-2026"), candle(2, "17-Aug-2026"))))
                .build();

        var response = KronosPredictionResponse.fromKronosPrediction(predictions,
                List.of(NSEHistoricalData.builder().symbol("TCS").close(3200).build()));

        assertEquals(List.of("16-Aug-2026", "17-Aug-2026", "18-Aug-2026"),
                response.getPredictions().stream().map(NSEHistoricalData::getTimestamp).toList());
        assertEquals("TCS", response.getSymbol());
        assertEquals(1, response.getHistoricalData().size());
    }

    private KronosPredictions.PredictedCandle candle(int horizon, String date) {
        return KronosPredictions.PredictedCandle.builder()
                .horizonDay(horizon).date(date)
                .open(new BigDecimal("1")).high(new BigDecimal("2"))
                .low(new BigDecimal("0.5")).close(new BigDecimal("1.5"))
                .build();
    }

    // --- Misc -------------------------------------------------------------

    @Test
    void authCallbackResponse_distinguishesRedirectFromSession() {
        var redirect = AuthCallbackResponse.redirect("https://app.example.com/cb");
        assertTrue(redirect.isRedirect());
        assertNull(redirect.cookie());

        var session = AuthCallbackResponse.session("auth_token=x", UserDto.builder().userId(1L).build(), "ok");
        assertFalse(session.isRedirect());
        assertEquals("auth_token=x", session.cookie());
        assertEquals(1L, session.user().getUserId());
    }

    @Test
    void targetStockResult_exposesTheSymbolAndIsNullSafe() {
        assertEquals("TCS", new TargetStockResult(Margin.builder().symbol("TCS").build(), 10).getSymbol());
        assertNull(new TargetStockResult(null, 0).getSymbol());
    }

    @Test
    void brevoEmailRequest_buildsDistinctTemplatesPerOtpPurpose() {
        var signup = BrevoEmailRequest.create("jane@example.com", "123456", OtpFor.REGISTER, "no-reply@x.com");
        var update = BrevoEmailRequest.create("jane@example.com", "123456", OtpFor.UPDATE, "no-reply@x.com");

        assertEquals("jane", signup.getTo().getFirst().getName());
        assertEquals("no-reply@x.com", signup.getSender().getEmail());
        assertTrue(signup.getHtmlContent().contains("123456"));
        assertFalse(signup.getSubject().equals(update.getSubject()), "each purpose needs its own subject line");
    }
}
