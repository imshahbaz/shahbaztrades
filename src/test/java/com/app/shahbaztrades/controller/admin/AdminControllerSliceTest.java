package com.app.shahbaztrades.controller.admin;

import com.app.shahbaztrades.components.helper.MarketDataContainer;
import com.app.shahbaztrades.components.observer.MarketTickPipeline;
import com.app.shahbaztrades.components.observer.TradeWatchdog;
import com.app.shahbaztrades.controller.StrategyTradingController;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.GlobalExceptionHandler;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.order.StrategyOrderDto;
import com.app.shahbaztrades.model.dto.scheduler.CronTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.ScheduledTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.SchedulerCallBackDto;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.model.enums.SchedulerTaskType;
import com.app.shahbaztrades.model.enums.TimeFrame;
import com.app.shahbaztrades.service.AngelOneService;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.service.SchedulerService;
import com.app.shahbaztrades.service.StrategyOrderService;
import com.app.shahbaztrades.service.StrategyService;
import com.app.shahbaztrades.service.TradeEngine;
import com.app.shahbaztrades.util.DateUtil;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerSliceTest {

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private static MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Config {

        @Mock
        private MongoConfigService mongoConfigService;

        @Test
        void reloadEndpoints_refreshTheirOwnCache() throws Exception {
            var controller = new ConfigControllerAdmin(mongoConfigService);

            mvc(controller).perform(post("/api/admin/config/reload")).andExpect(status().isOk());
            mvc(controller).perform(post("/api/admin/config/client/reload")).andExpect(status().isOk());

            verify(mongoConfigService).refreshConfig();
            verify(mongoConfigService).refreshClientConfig();
        }

        @Test
        void getActiveConfig_exposesTheFullServerConfig() throws Exception {
            var config = new MongoEnvConfig();
            config.setId("mongoConfig");
            when(mongoConfigService.getConfig()).thenReturn(config);

            mvc(new ConfigControllerAdmin(mongoConfigService)).perform(get("/api/admin/config/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value("mongoConfig"));
        }

        @Test
        void updateConfig_forwardsThePatchMap() throws Exception {
            mvc(new ConfigControllerAdmin(mongoConfigService))
                    .perform(put("/api/admin/config/update/mongoConfig")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"leverage\":4.0}"))
                    .andExpect(status().isOk());

            verify(mongoConfigService).updatePartialConfig(eq("mongoConfig"), any(Map.class));
        }

        @Test
        void updateConfig_mapsAnUnknownFieldTo400() throws Exception {
            doThrow(new BadRequestException("Invalid field provided in update request"))
                    .when(mongoConfigService).updatePartialConfig(any(), any());

            mvc(new ConfigControllerAdmin(mongoConfigService))
                    .perform(put("/api/admin/config/update/mongoConfig")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"nope\":1}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Scheduler {

        @Mock
        private SchedulerService schedulerService;

        private CronTaskDto cron() {
            return new CronTaskDto("cron-1",
                    new SchedulerCallBackDto("https://example.com/hook", "POST", null, Map.of()),
                    "0 * * * * ?");
        }

        @Test
        void scheduleTask_returnsTheGeneratedTaskId() throws Exception {
            var task = new ScheduledTaskDto(
                    new SchedulerCallBackDto("https://example.com/hook", "POST", null, Map.of()),
                    System.currentTimeMillis() + Duration.ofMinutes(5).toMillis(), null);
            when(schedulerService.scheduleTask(any(ScheduledTaskDto.class))).thenReturn("task-1");

            mvc(new SchedulerController(schedulerService))
                    .perform(post("/api/admin/schedule")
                            .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(task)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("task-1"));
        }

        @Test
        void scheduleCron_mapsADuplicateIdTo409() throws Exception {
            when(schedulerService.scheduleCron(any(CronTaskDto.class)))
                    .thenThrow(new ResourceAlreadyExistsException("Cron with cron-1 already exists"));

            mvc(new SchedulerController(schedulerService))
                    .perform(post("/api/admin/schedule/cron")
                            .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(cron())))
                    .andExpect(status().isConflict());
        }

        @Test
        void deleteEndpoints_targetTheCorrectTaskType() throws Exception {
            var controller = new SchedulerController(schedulerService);
            when(schedulerService.deleteTask(eq("id-1"), any(SchedulerTaskType.class))).thenReturn(true);

            mvc(controller).perform(delete("/api/admin/schedule").param("id", "id-1"))
                    .andExpect(status().isOk());
            mvc(controller).perform(delete("/api/admin/schedule/cron").param("id", "id-1"))
                    .andExpect(status().isOk());

            verify(schedulerService).deleteTask("id-1", SchedulerTaskType.TASK);
            verify(schedulerService).deleteTask("id-1", SchedulerTaskType.CRON);
        }

        @Test
        void getTaskById_mapsAnUnknownIdTo404() throws Exception {
            when(schedulerService.getTask(eq("missing"), any())).thenThrow(new NotFoundException("Task not found"));

            mvc(new SchedulerController(schedulerService))
                    .perform(get("/api/admin/schedule/missing").param("taskType", "CRON"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void getAllTasks_bindsTheTaskTypeEnum() throws Exception {
            when(schedulerService.getAllTask(SchedulerTaskType.CRON)).thenReturn(List.of());

            mvc(new SchedulerController(schedulerService))
                    .perform(get("/api/admin/schedule/all").param("taskType", "CRON"))
                    .andExpect(status().isOk());

            verify(schedulerService).getAllTask(SchedulerTaskType.CRON);
        }

        @Test
        void updateCron_forwardsBothThePathIdAndTheBody() throws Exception {
            when(schedulerService.updateCron(eq("cron-1"), any(CronTaskDto.class))).thenReturn("cron-1");

            mvc(new SchedulerController(schedulerService))
                    .perform(put("/api/admin/schedule/cron/cron-1")
                            .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(cron())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("cron-1"));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Strategies {

        @Mock
        private StrategyService strategyService;
        @Mock
        private StrategyOrderService strategyOrderService;

        private StrategyDto dto() {
            return StrategyDto.builder()
                    .name("RSI15MIN").scanClause("close > 100").active(true).timeFrame(TimeFrame.FIFTEEN_MINUTE)
                    .build();
        }

        @Test
        void createStrategy_mapsADuplicateNameTo409() throws Exception {
            when(strategyService.createStrategy(any(StrategyDto.class)))
                    .thenThrow(new ResourceAlreadyExistsException("already exists"));

            mvc(new StrategyControllerAdmin(strategyService))
                    .perform(post("/api/admin/strategy")
                            .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(dto())))
                    .andExpect(status().isConflict());
        }

        @Test
        void createStrategy_rejectsAStrategyWithNoScanClause() throws Exception {
            var invalid = dto();
            invalid.setScanClause("");

            mvc(new StrategyControllerAdmin(strategyService))
                    .perform(post("/api/admin/strategy")
                            .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void updateStrategy_returnsTheSavedDefinition() throws Exception {
            when(strategyService.updateStrategy(any(StrategyDto.class))).thenReturn(dto());

            mvc(new StrategyControllerAdmin(strategyService))
                    .perform(put("/api/admin/strategy")
                            .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(dto())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("RSI15MIN"));
        }

        @Test
        void deleteStrategy_upperCasesTheIdBeforeDeleting() throws Exception {
            // Strategies are stored under their canonical upper-case name.
            mvc(new StrategyControllerAdmin(strategyService))
                    .perform(delete("/api/admin/strategy").param("id", "rsi15min"))
                    .andExpect(status().isOk());

            verify(strategyService).deleteStrategy("RSI15MIN");
        }

        @Test
        void getAllStrategiesAdmin_returnsInactiveOnesToo() throws Exception {
            when(strategyService.getAllStrategiesAdmin()).thenReturn(List.of(dto()));

            mvc(new StrategyControllerAdmin(strategyService)).perform(get("/api/admin/strategy/admin"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        void getAllOrdersAdmin_filtersByStrategyName() throws Exception {
            when(strategyOrderService.getAllOrdersAdmin("RSI15MIN")).thenReturn(List.of(
                    StrategyOrderDto.builder().id("s1").strategyName("RSI15MIN")
                            .date(DateUtil.getTodayDate().toString()).amount(new BigDecimal("10000"))
                            .broker(BrokerType.RUPEEZY).build()));

            mvc(new StrategyOrderControllerAdmin(strategyOrderService))
                    .perform(get("/api/admin/strategy-order").param("strategyName", "RSI15MIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value("s1"));
        }

        @Test
        void getOrderByIdAdmin_mapsAnUnknownIdTo404() throws Exception {
            when(strategyOrderService.getOrderById("nope"))
                    .thenThrow(new NotFoundException("Strategy order not found"));

            mvc(new StrategyOrderControllerAdmin(strategyOrderService))
                    .perform(get("/api/admin/strategy-order/nope"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Monitoring {

        @Mock
        private MarketTickPipeline marketTickPipeline;
        @Mock
        private TradeWatchdog tradeWatchdog;
        @Mock
        private AngelOneService angelOneService;
        @Mock
        private TradeEngine tradeEngine;
        @Mock
        private MarketDataContainer marketDataContainer;

        @Test
        void serverStats_reportsPipelineWatchdogAndWebsocketState() throws Exception {
            when(marketTickPipeline.getRingBufferSize()).thenReturn(16384);
            when(marketTickPipeline.getShardCount()).thenReturn(4);
            when(marketTickPipeline.getRemainingCapacity()).thenReturn(16000L);
            when(tradeWatchdog.getWatchedTokenCount()).thenReturn(3);
            when(angelOneService.isWebSocketConnected()).thenReturn(true);
            when(angelOneService.getReconnectAttempts()).thenReturn(2);

            mvc(new ServerMonitorController(marketTickPipeline, tradeWatchdog, angelOneService))
                    .perform(get("/api/admin/server/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.domain.pipeline.ringUsedSlots").value(384))
                    .andExpect(jsonPath("$.data.domain.watchdog.watchedTokens").value(3))
                    .andExpect(jsonPath("$.data.domain.webSocket.connected").value(true))
                    .andExpect(jsonPath("$.data.domain.webSocket.reconnectAttempts").value(2));
        }

        @Test
        void serverStats_reportsMinusOneUsedSlotsBeforeThePipelineStarts() throws Exception {
            when(marketTickPipeline.getRemainingCapacity()).thenReturn(-1L);

            mvc(new ServerMonitorController(marketTickPipeline, tradeWatchdog, angelOneService))
                    .perform(get("/api/admin/server/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.domain.pipeline.ringUsedSlots").value(-1));
        }

        @Test
        void tradingLifecycleEndpoints_triggerTheirComponents() throws Exception {
            var controller = new StrategyTradingController(tradeEngine, marketDataContainer, angelOneService);

            mvc(controller).perform(post("/api/strategy-trading/continuous")).andExpect(status().isOk());
            mvc(controller).perform(post("/api/strategy-trading/warmup")).andExpect(status().isOk());
            mvc(controller).perform(post("/api/strategy-trading/start-container")).andExpect(status().isOk());

            verify(tradeEngine).continuousTrade();
            verify(marketDataContainer).warmupContainer();
            verify(marketDataContainer).startWorkersForActiveWatchlist(any());
        }
    }
}
