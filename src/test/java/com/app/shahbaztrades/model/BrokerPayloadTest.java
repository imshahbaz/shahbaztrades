package com.app.shahbaztrades.model;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezyOrderHistory;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezyOrderResponseDto;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezySessionRequest;
import com.app.shahbaztrades.model.dto.sessionmanager.ZerodhaLoginRequestDTO;
import com.app.shahbaztrades.model.dto.sessionmanager.ZerodhaLoginResponseDTO;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.util.TotpUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the request/response shapes exchanged with AngelOne, Rupeezy and the session manager. */
class BrokerPayloadTest {

    // --- AngelOne ---------------------------------------------------------

    @Test
    void smartApiResponse_isSuccessOnlyWithTrueStatusAndABody() {
        assertTrue(new SmartApiLtpResponse<>(true, "ok", null, "data").isSuccess());
        assertFalse(new SmartApiLtpResponse<>(false, "no", "E1", "data").isSuccess());
        assertFalse(new SmartApiLtpResponse<>(null, null, null, "data").isSuccess());
        assertFalse(new SmartApiLtpResponse<>(true, "ok", null, null).isSuccess());
    }

    @Test
    void getHistoricalCandles_mapsTheRawCandleArrays() {
        List<List<Object>> raw = List.of(
                List.of("2026-08-14T09:15:00+05:30", 100.0, 105.0, 99.0, 104.0, 1000L),
                List.of("2026-08-15T09:15:00+05:30", 104.0, 108.0, 103.0, 107.0, 2000L));

        var candles = new SmartApiLtpResponse<>(true, "ok", null, raw).getHistoricalCandles();

        assertEquals(2, candles.size());
        assertEquals(100.0, candles.getFirst().open());
        assertEquals(105.0, candles.getFirst().high());
        assertEquals(99.0, candles.getFirst().low());
        assertEquals(104.0, candles.getFirst().close());
        assertEquals(2026, candles.getFirst().timestamp().getYear());
    }

    @Test
    void getHistoricalCandles_skipsEmptyRows() {
        List<List<Object>> raw = new java.util.ArrayList<>();
        raw.add(List.of());
        raw.add(List.of("2026-08-15T09:15:00+05:30", 1.0, 2.0, 0.5, 1.5, 1L));

        assertEquals(1, new SmartApiLtpResponse<>(true, "ok", null, raw).getHistoricalCandles().size());
    }

    @Test
    void getHistoricalCandles_failsLoudlyOnAnUnsuccessfulResponse() {
        // Silently returning an empty list here would look like "no market data" instead of an auth error.
        var response = new SmartApiLtpResponse<List<List<Object>>>(false, "Invalid token", "AB1001", null);
        assertThrows(NotFoundException.class, response::getHistoricalCandles);
    }

    // --- Rupeezy ----------------------------------------------------------

    @Test
    void rupeezyResponse_isSuccessOnlyForTheLiteralSuccessStatus() {
        var history = new RupeezyOrderHistory();
        history.setStatus("success");
        assertTrue(history.isSuccess());

        history.setStatus("SUCCESS");
        assertFalse(history.isSuccess(), "the broker sends lower-case; anything else is an error payload");

        history.setStatus(null);
        assertFalse(history.isSuccess());
    }

    @Test
    void rupeezyOrderHistory_findsAnOrderByIdAndIsNullSafe() {
        var history = new RupeezyOrderHistory();
        assertNull(history.getOrder("O1"), "a missing orders list must not NPE");

        history.setOrders(List.of(
                RupeezyOrderHistory.OrderData.builder().orderId("O1").status("COMPLETE").build(),
                RupeezyOrderHistory.OrderData.builder().orderId("O2").status("OPEN").build()));

        assertEquals("OPEN", history.getOrder("O2").getStatus());
        assertNull(history.getOrder("O3"));
    }

    @Test
    void rupeezyOrderResponse_returnsANullOrderIdWhenThePayloadHasNoData() {
        var response = new RupeezyOrderResponseDto();
        assertNull(response.getOrderId());

        response.setData(RupeezyOrderResponseDto.OrderData.builder().orderId("O9").build());
        assertEquals("O9", response.getOrderId());
    }

    @Test
    void rupeezySessionRequest_signsTheChecksumOverAppIdTokenAndSecret() {
        var request = RupeezySessionRequest.builder().applicationId("app").token("tok").build();

        request.addChecksum("secret");

        assertEquals(TotpUtil.generateChecksum("app", "tok", "secret"), request.getChecksum());
    }

    @Test
    void rupeezySessionRequest_refusesToSignWithMissingFields() {
        assertThrows(BadRequestException.class,
                () -> RupeezySessionRequest.builder().applicationId("app").build().addChecksum("secret"));
        assertThrows(BadRequestException.class,
                () -> RupeezySessionRequest.builder().applicationId("app").token("tok").build().addChecksum(""));
    }

    // --- Session manager --------------------------------------------------

    @Test
    void zerodhaLoginResponse_classifiesStatusCaseInsensitively() {
        assertTrue(ZerodhaLoginResponseDTO.builder().status("pending").build().isPending());
        assertTrue(ZerodhaLoginResponseDTO.builder().status("SUCCESS").build().isSuccess());
        assertTrue(ZerodhaLoginResponseDTO.builder().status("Error").build().isError());

        var success = ZerodhaLoginResponseDTO.builder().status("SUCCESS").build();
        assertFalse(success.isError());
        assertFalse(success.isPending());
    }

    @Test
    void zerodhaLoginResponse_treatsAnUnknownStatusAsNeitherSuccessNorError() {
        var unknown = ZerodhaLoginResponseDTO.builder().status("QUEUED").build();
        assertFalse(unknown.isSuccess());
        assertFalse(unknown.isError());
        assertFalse(unknown.isPending());
    }

    @Test
    void zerodhaLoginRequest_carriesTheAutoLoginCredentials() {
        var config = new User.ZerodhaConfig();
        config.setApiKey("key");
        config.setUserName("kite-user");
        config.setPassword("pw");
        config.setTotpSecret("SEED");

        ZerodhaLoginRequestDTO request = ZerodhaLoginRequestDTO.mapDto(42L, config);

        assertEquals(42L, request.userid());
        assertEquals("kite-user", request.username());
        assertEquals("SEED", request.totpSecret());
        assertEquals("key", request.apiKey());
        assertNotNull(request.password());
    }
}
