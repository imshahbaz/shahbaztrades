package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.exceptions.GlobalExceptionHandler;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.analysis.AIAnalysis;
import com.app.shahbaztrades.model.dto.analysis.TradingViewNewsResponse;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;
import com.app.shahbaztrades.model.dto.auth.AuthCallbackResponse;
import com.app.shahbaztrades.model.dto.auth.AuthCookieResponse;
import com.app.shahbaztrades.model.dto.chartink.StockMarginDto;
import com.app.shahbaztrades.model.dto.holdings.HoldingDto;
import com.app.shahbaztrades.model.dto.kronos.BulkPredictionRequestDto;
import com.app.shahbaztrades.model.dto.kronos.KronosPredictionResponse;
import com.app.shahbaztrades.model.dto.nse.NSEHistoricalData;
import com.app.shahbaztrades.model.dto.order.StrategyOrderDto;
import com.app.shahbaztrades.model.dto.sessionmanager.ZerodhaLoginResponseDTO;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import com.app.shahbaztrades.model.dto.zerodha.BrokerLoginDto;
import com.app.shahbaztrades.model.entity.ClientConfigurations;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.model.enums.TimeFrame;
import com.app.shahbaztrades.model.enums.UserTheme;
import com.app.shahbaztrades.service.AnalysisService;
import com.app.shahbaztrades.service.AngelOneService;
import com.app.shahbaztrades.service.AuthService;
import com.app.shahbaztrades.service.ChartInkService;
import com.app.shahbaztrades.service.FcmService;
import com.app.shahbaztrades.service.HoldingsService;
import com.app.shahbaztrades.service.KronosPredictionService;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.service.NseService;
import com.app.shahbaztrades.service.RupeezyService;
import com.app.shahbaztrades.service.SessionManagerService;
import com.app.shahbaztrades.service.StrategyOrderService;
import com.app.shahbaztrades.service.StrategyService;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.service.ZerodhaService;
import com.app.shahbaztrades.util.Constants;
import com.app.shahbaztrades.util.DateUtil;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc slices for the user-facing controllers: routing, binding and error translation only. */
class ControllerSliceTest {

    private static final UserDto USER = UserDto.builder().userId(7L).build();
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private static MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Health {

        @Test
        void health_reportsUp() throws Exception {
            mvc(new HealthController()).perform(get("/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Auth {

        @Mock
        private AuthService authService;

        @Test
        void logout_sendsTheClearingCookie() throws Exception {
            when(authService.logout()).thenReturn("auth_token=; Path=/; HttpOnly");

            mvc(new AuthController(authService)).perform(post("/api/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, "auth_token=; Path=/; HttpOnly"));
        }

        @Test
        void getCurrentUser_readsTheUserFromTheRequestAttribute() throws Exception {
            when(authService.getMe(any(UserDto.class))).thenReturn(USER);

            mvc(new AuthController(authService))
                    .perform(get("/api/auth/me").requestAttr("user", USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(7));
        }

        @Test
        void validateGoogleToken_setsTheCookieOnlyWhenTheServiceSuppliesOne() throws Exception {
            when(authService.validateGoogleToken(eq("code"), eq(false)))
                    .thenReturn(new AuthCookieResponse<>("handle", "Processing token", null));

            mvc(new AuthController(authService))
                    .perform(post("/api/auth/google/token").param("code", "code"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                    .andExpect(jsonPath("$.data").value("handle"));
        }

        @Test
        void googleCallback_returnsA307RedirectForTheRedirectFlow() throws Exception {
            when(authService.googleAuthCallback(any(), any()))
                    .thenReturn(AuthCallbackResponse.redirect("https://app.example.com/cb"));

            mvc(new AuthController(authService))
                    .perform(get("/api/auth/google/callback").param("code", "c").param("state", "redirect|x"))
                    .andExpect(status().isTemporaryRedirect())
                    .andExpect(header().string(HttpHeaders.LOCATION, "https://app.example.com/cb"));
        }

        @Test
        void googleCallback_returnsTheSessionCookieForTheStandardFlow() throws Exception {
            when(authService.googleAuthCallback(any(), any()))
                    .thenReturn(AuthCallbackResponse.session("auth_token=jwt", USER, "User created"));

            mvc(new AuthController(authService))
                    .perform(get("/api/auth/google/callback").param("code", "c").param("state", "standard"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, "auth_token=jwt"))
                    .andExpect(jsonPath("$.data.userId").value(7));
        }

        @Test
        void googleCallback_mapsAnInvalidStateTo401() throws Exception {
            when(authService.googleAuthCallback(any(), any()))
                    .thenThrow(new UnauthorizedException("Invalid state"));

            mvc(new AuthController(authService))
                    .perform(get("/api/auth/google/callback").param("code", "c").param("state", "bogus"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Holdings {

        @Mock
        private HoldingsService holdingsService;

        @Test
        void getAllHoldings_bindsTheBrokerTypeEnum() throws Exception {
            when(holdingsService.getAllHoldings(eq(BrokerType.ZERODHA), any(UserDto.class)))
                    .thenReturn(List.of(HoldingDto.builder().symbol("TCS").build()));

            mvc(new HoldingsControllers(holdingsService))
                    .perform(get("/api/holdings/all").param("brokerType", "ZERODHA").requestAttr("user", USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].symbol").value("TCS"));
        }

        @Test
        void getAllHoldings_rejectsAnUnknownBrokerTypeWith400() throws Exception {
            mvc(new HoldingsControllers(holdingsService))
                    .perform(get("/api/holdings/all").param("brokerType", "ROBINHOOD").requestAttr("user", USER))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void getAllHoldings_mapsAMissingPortfolioTo404() throws Exception {
            when(holdingsService.getAllHoldings(any(), any())).thenThrow(new NotFoundException("Holdings not found"));

            mvc(new HoldingsControllers(holdingsService))
                    .perform(get("/api/holdings/all").param("brokerType", "ZERODHA").requestAttr("user", USER))
                    .andExpect(status().isNotFound());
        }

        @Test
        void deleteHoldings_bindsBothThePathVariableAndTheBrokerParam() throws Exception {
            when(holdingsService.deleteHoldings(eq(BrokerType.ZERODHA), any(), eq("TCS"))).thenReturn(true);

            mvc(new HoldingsControllers(holdingsService))
                    .perform(delete("/api/holdings/TCS").param("brokerType", "ZERODHA").requestAttr("user", USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        void deleteHoldingDetail_bindsTheSymbolAndRowId() throws Exception {
            when(holdingsService.deleteHoldingDetail(eq(BrokerType.ZERODHA), any(), eq("TCS"), eq(3)))
                    .thenReturn(true);

            mvc(new HoldingsControllers(holdingsService))
                    .perform(delete("/api/holdings/detail/TCS/3").param("brokerType", "ZERODHA")
                            .requestAttr("user", USER))
                    .andExpect(status().isOk());
        }

        @Test
        void updatePortfolio_triggersTheAsyncRefresh() throws Exception {
            mvc(new HoldingsControllers(holdingsService)).perform(post("/api/holdings/update-portfolio"))
                    .andExpect(status().isOk());

            verify(holdingsService).updatePortfolio();
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class StrategyOrders {

        @Mock
        private StrategyOrderService strategyOrderService;

        private StrategyOrderDto dto() {
            return StrategyOrderDto.builder()
                    .strategyName("RSI15MIN").date(DateUtil.getTodayDate().plusDays(1).toString())
                    .amount(new BigDecimal("10000")).broker(BrokerType.RUPEEZY)
                    .build();
        }

        @Test
        void createOrder_stampsTheAuthenticatedUserIdOntoTheRequest() throws Exception {
            // A client-supplied userId must never let one user book against another's account.
            var body = dto();
            body.setUserId(999L);
            when(strategyOrderService.createOrder(any(StrategyOrderDto.class))).thenReturn(dto());

            mvc(new StrategyOrderController(strategyOrderService))
                    .perform(post("/api/strategy-order").requestAttr("user", USER)
                            .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)))
                    .andExpect(status().isCreated());

            ArgumentCaptor<StrategyOrderDto> captured = ArgumentCaptor.forClass(StrategyOrderDto.class);
            verify(strategyOrderService).createOrder(captured.capture());
            assertEquals(7L, captured.getValue().getUserId());
        }

        @Test
        void updateOrder_stampsBothThePathIdAndTheAuthenticatedUserId() throws Exception {
            var body = dto();
            body.setId("spoofed");
            body.setUserId(999L);
            when(strategyOrderService.updateOrder(any(StrategyOrderDto.class))).thenReturn(dto());

            mvc(new StrategyOrderController(strategyOrderService))
                    .perform(put("/api/strategy-order/s1").requestAttr("user", USER)
                            .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)))
                    .andExpect(status().isOk());

            ArgumentCaptor<StrategyOrderDto> captured = ArgumentCaptor.forClass(StrategyOrderDto.class);
            verify(strategyOrderService).updateOrder(captured.capture());
            assertEquals("s1", captured.getValue().getId());
            assertEquals(7L, captured.getValue().getUserId());
        }

        @Test
        void getMyOrders_scopesTheLookupToTheAuthenticatedUser() throws Exception {
            when(strategyOrderService.getOrdersByUserId(7L)).thenReturn(List.of(dto()));

            mvc(new StrategyOrderController(strategyOrderService))
                    .perform(get("/api/strategy-order/my").requestAttr("user", USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));

            verify(strategyOrderService).getOrdersByUserId(7L);
        }

        @Test
        void deleteOrder_mapsAMissingOrderTo404() throws Exception {
            doThrow(new NotFoundException("Strategy order not found"))
                    .when(strategyOrderService).deleteOrder("nope");

            mvc(new StrategyOrderController(strategyOrderService))
                    .perform(delete("/api/strategy-order/nope"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class MarketAndAnalysis {

        @Mock
        private MarginService marginService;
        @Mock
        private ChartInkService chartInkService;
        @Mock
        private AnalysisService analysisService;
        @Mock
        private NseService nseService;
        @Mock
        private AngelOneService angelOneService;
        @Mock
        private KronosPredictionService kronosPredictionService;
        @Mock
        private StrategyService strategyService;

        @Test
        void getMargin_upperCasesTheSymbolBeforeLookup() throws Exception {
            when(marginService.getMargin("TCS")).thenReturn(Margin.builder().symbol("TCS").build());

            mvc(new MarginController(marginService)).perform(get("/api/margin/symbol/tcs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.symbol").value("TCS"));
        }

        @Test
        void getMargin_mapsAnUnknownSymbolTo404() throws Exception {
            when(marginService.getMargin("NOPE")).thenThrow(new NotFoundException("Margin not found"));

            mvc(new MarginController(marginService)).perform(get("/api/margin/symbol/nope"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void syncMtf_forwardsTheUploadedFileBytes() throws Exception {
            var file = new MockMultipartFile("file", "mtf.json", MediaType.APPLICATION_JSON_VALUE,
                    "{}".getBytes());

            mvc(new MarginController(marginService)).perform(multipart("/api/margin/json").file(file))
                    .andExpect(status().isOk());

            verify(marginService).syncMTF("{}".getBytes());
        }

        @Test
        void chartInkEndpoints_delegateWithTheStrategyParam() throws Exception {
            when(chartInkService.fetchWithMargin("RSI15MIN"))
                    .thenReturn(List.of(StockMarginDto.builder().symbol("TCS").build()));

            mvc(new ChartInkController(chartInkService))
                    .perform(get("/api/chartink/fetchWithMargin").param("strategy", "RSI15MIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].symbol").value("TCS"));
        }

        @Test
        void getStockNews_returnsTheNewsItems() throws Exception {
            when(analysisService.getStockNews("TCS"))
                    .thenReturn(List.of(new TradingViewNewsResponse.NewsItem("Headline", 1L)));

            mvc(new NewsController(analysisService)).perform(get("/api/news/TCS"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].title").value("Headline"));
        }

        @Test
        void getAiAnalysis_mapsAMissingAnalysisTo404() throws Exception {
            when(analysisService.getGenAiAnalysis("TCS")).thenThrow(new NotFoundException("Analysis Not Found"));

            mvc(new NewsController(analysisService)).perform(get("/api/news/ai/TCS"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void getAiAnalysis_returnsTheParsedModelOutput() throws Exception {
            var analysis = new AIAnalysis();
            analysis.setAction("BUY");
            when(analysisService.getGenAiAnalysis("TCS")).thenReturn(analysis);

            mvc(new NewsController(analysisService)).perform(get("/api/news/ai/TCS"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.action").value("BUY"));
        }

        @Test
        void getNseHistory_delegatesWithTheSymbolParam() throws Exception {
            when(nseService.getHistoricalData("TCS"))
                    .thenReturn(List.of(NSEHistoricalData.builder().symbol("TCS").close(3200).build()));

            mvc(new NseController(nseService)).perform(get("/api/nse/history").param("symbol", "TCS"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        void getLtp_returnsTheMarketTicker() throws Exception {
            var ticker = new SmartApiLtpResponse.MarketTicker();
            ticker.setLtp(3200.0);
            when(angelOneService.getMarketTicker("11536")).thenReturn(ticker);

            mvc(new AngelOneController(angelOneService))
                    .perform(get("/api/angelone/ltp").param("token", "11536"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.ltp").value(3200.0));
        }

        @Test
        void websocketLifecycleEndpoints_delegateToTheService() throws Exception {
            var controller = new AngelOneController(angelOneService);

            mvc(controller).perform(post("/api/angelone/ws/connect")).andExpect(status().isOk());
            mvc(controller).perform(post("/api/angelone/ws/disconnect")).andExpect(status().isOk());
            mvc(controller).perform(post("/api/angelone/refresh-session")).andExpect(status().isOk());

            verify(angelOneService).startWebSocket();
            verify(angelOneService).disconnect();
            verify(angelOneService).refreshBrokerSession();
        }

        @Test
        void savePredictions_acceptsABulkPayload() throws Exception {
            var request = BulkPredictionRequestDto.builder().model("kronos").predictions(List.of()).build();

            mvc(new KronosPredictionController(kronosPredictionService))
                    .perform(post("/api/kronos/predictions")
                            .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(kronosPredictionService).savePredictions(any(BulkPredictionRequestDto.class));
        }

        @Test
        void getPredictions_mapsAMissingRunTo404() throws Exception {
            when(kronosPredictionService.getPredictions("NOPE"))
                    .thenThrow(new NotFoundException("Prediction not found"));

            mvc(new KronosPredictionController(kronosPredictionService))
                    .perform(get("/api/kronos/predictions").param("symbol", "NOPE"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void getPredictions_returnsTheCombinedResponse() throws Exception {
            when(kronosPredictionService.getPredictions("TCS"))
                    .thenReturn(KronosPredictionResponse.builder().symbol("TCS").build());

            mvc(new KronosPredictionController(kronosPredictionService))
                    .perform(get("/api/kronos/predictions").param("symbol", "TCS"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.symbol").value("TCS"));
        }

        @Test
        void getAllStrategies_defaultsToTheDailyTimeframe() throws Exception {
            when(strategyService.getAllStrategies(TimeFrame.DAILY))
                    .thenReturn(List.of(StrategyDto.builder().name("RSI").build()));

            mvc(new StrategyController(strategyService)).perform(get("/api/strategy"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("RSI"));

            verify(strategyService).getAllStrategies(TimeFrame.DAILY);
        }

        @Test
        void getAllStrategies_honoursAnExplicitTimeframe() throws Exception {
            when(strategyService.getAllStrategies(TimeFrame.FIFTEEN_MINUTE)).thenReturn(List.of());

            mvc(new StrategyController(strategyService))
                    .perform(get("/api/strategy").param("timeFrame", "FIFTEEN_MINUTE"))
                    .andExpect(status().isOk());

            verify(strategyService).getAllStrategies(TimeFrame.FIFTEEN_MINUTE);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Brokers {

        @Mock
        private ZerodhaService zerodhaService;
        @Mock
        private RupeezyService rupeezyService;
        @Mock
        private SessionManagerService sessionManagerService;

        @Test
        void zerodhaLogin_forwardsTheSnakeCaseRequestBody() throws Exception {
            mvc(new ZerodhaController(zerodhaService))
                    .perform(post("/api/zerodha/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"request_token\":\"req\",\"user_id\":7}"))
                    .andExpect(status().isOk());

            verify(zerodhaService).login(new BrokerLoginDto("req", 7L));
        }

        @Test
        void zerodhaLogin_rejectsABlankRequestTokenWith400() throws Exception {
            mvc(new ZerodhaController(zerodhaService))
                    .perform(post("/api/zerodha/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"request_token\":\"\",\"user_id\":7}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void zerodhaMe_returnsTheServiceEnvelopeVerbatim() throws Exception {
            when(zerodhaService.getAuth(any(UserDto.class)))
                    .thenReturn(ApiResponse.<String>builder()
                            .success(false).data("api-key").message("Token expired").build());

            mvc(new ZerodhaController(zerodhaService))
                    .perform(get("/api/zerodha/me").requestAttr("user", USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data").value("api-key"));
        }

        @Test
        void zerodhaSetConfig_bindsTheConfigBodyAndTheAuthenticatedUser() throws Exception {
            when(zerodhaService.setConfig(any(User.ZerodhaConfig.class), any(UserDto.class))).thenReturn(7L);

            mvc(new ZerodhaController(zerodhaService))
                    .perform(post("/api/zerodha/config").requestAttr("user", USER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"apiKey\":\"key\",\"apiSecret\":\"secret\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(7));
        }

        @Test
        void rupeezyMe_returnsTheServiceEnvelopeVerbatim() throws Exception {
            when(rupeezyService.getAuth(any(UserDto.class))).thenReturn(ApiResponse.ok("7", "Token already exist"));

            mvc(new RupeezyController(rupeezyService))
                    .perform(get("/api/rupeezy/me").requestAttr("user", USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("7"));
        }

        @Test
        void zerodhaCallback_requiresTheSessionManagerSourceHeader() throws Exception {
            var controller = new SessionManagerController(sessionManagerService, zerodhaService, rupeezyService);
            String body = JSON.writeValueAsString(
                    ZerodhaLoginResponseDTO.builder().status("SUCCESS").userid(7L).requestToken("req").build());

            mvc(controller).perform(post("/api/session-manager/zerodha-callback")
                            .header("source", "attacker")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());

            verify(zerodhaService, org.mockito.Mockito.never())
                    .sessionManagerCallback(any(ZerodhaLoginResponseDTO.class));
        }

        @Test
        void zerodhaCallback_acceptsTheCorrectSourceHeader() throws Exception {
            var controller = new SessionManagerController(sessionManagerService, zerodhaService, rupeezyService);
            String body = JSON.writeValueAsString(
                    ZerodhaLoginResponseDTO.builder().status("SUCCESS").userid(7L).requestToken("req").build());

            mvc(controller).perform(post("/api/session-manager/zerodha-callback")
                            .header("source", Constants.SESSION_MANAGER_SOURCE)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());

            verify(zerodhaService).sessionManagerCallback(any(ZerodhaLoginResponseDTO.class));
        }

        @Test
        void revokeBrokerAuth_routesToTheRequestedBroker() throws Exception {
            var controller = new SessionManagerController(sessionManagerService, zerodhaService, rupeezyService);

            mvc(controller).perform(post("/api/session-manager/broker/revoke-auth")
                    .param("userId", "7").param("brokerType", "ZERODHA")).andExpect(status().isOk());
            mvc(controller).perform(post("/api/session-manager/broker/revoke-auth")
                    .param("userId", "7").param("brokerType", "RUPEEZY")).andExpect(status().isOk());

            verify(zerodhaService).revokeZerodhaAuth(7L);
            verify(rupeezyService).revokeRupeezyAuth(7L);
        }

        @Test
        void autoConnectZerodhaSession_mapsAnInFlightRequestTo409() throws Exception {
            var controller = new SessionManagerController(sessionManagerService, zerodhaService, rupeezyService);
            when(sessionManagerService.autoConnectZerodhaSession(any(UserDto.class)))
                    .thenThrow(new ResourceAlreadyExistsException("Request already exists"));

            mvc(controller).perform(post("/api/session-manager/zerodha-auto-connect").requestAttr("user", USER))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Users {

        @Mock
        private UserService userService;
        @Mock
        private FcmService fcmService;
        @Mock
        private MongoConfigService mongoConfigService;

        @Test
        void patchFcmToken_savesUnderTheAuthenticatedUserId() throws Exception {
            mvc(new UserController(userService, fcmService))
                    .perform(patch("/api/user/fcm-token").requestAttr("user", USER)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"fcm-1\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("fcm-1"));

            verify(fcmService).saveToken(7L, "fcm-1");
        }

        @Test
        void patchTheme_overridesTheBodyUserIdWithTheAuthenticatedOne() throws Exception {
            // Otherwise a client could flip another account's theme by posting their id.
            when(userService.updateUserTheme(any(UserDto.class))).thenReturn(UserTheme.LIGHT);

            mvc(new UserController(userService, fcmService))
                    .perform(patch("/api/user/theme").requestAttr("user", USER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":999,\"theme\":\"LIGHT\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("LIGHT"));

            ArgumentCaptor<UserDto> captured = ArgumentCaptor.forClass(UserDto.class);
            verify(userService).updateUserTheme(captured.capture());
            assertEquals(7L, captured.getValue().getUserId());
        }

        @Test
        void removeFcmToken_isReachableWithoutAuthentication() throws Exception {
            mvc(new UserController(userService, fcmService))
                    .perform(post("/api/user/fcm-token/remove")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"fcm-1\"}"))
                    .andExpect(status().isOk());

            verify(fcmService).removeToken("fcm-1");
        }

        @Test
        void getClientConfig_exposesTheClientScopedDocumentOnly() throws Exception {
            var config = new ClientConfigurations();
            config.setId("clientConfigId");
            when(mongoConfigService.getClientConfig()).thenReturn(config);

            mvc(new ConfigController(mongoConfigService)).perform(get("/api/config/client/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value("clientConfigId"));

            verify(mongoConfigService, org.mockito.Mockito.never()).getConfig();
        }
    }
}
