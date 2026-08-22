package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.exceptions.GlobalExceptionHandler;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.order.OrderDto;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.service.MtfTradeEngine;
import com.app.shahbaztrades.service.OrderService;
import com.app.shahbaztrades.util.DateUtil;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;
    @Mock
    private MtfTradeEngine mtfTradeEngine;
    @InjectMocks
    private OrderController controller;

    private MockMvc mockMvc;
    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private OrderDto dto() {
        return OrderDto.builder()
                .userId(7L).symbol("TCS").quantity(10)
                .date(DateUtil.getTodayDate().plusDays(1).toString())
                .broker(BrokerType.ZERODHA).strategyName("TRAILING PROFIT")
                .build();
    }

    @Test
    void getOrderById_returnsTheOrderInsideTheApiEnvelope() throws Exception {
        when(orderService.getById("o1")).thenReturn(dto());

        mockMvc.perform(get("/api/order/o1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.symbol").value("TCS"))
                .andExpect(jsonPath("$.message").value("Order fetched successfully"));
    }

    @Test
    void getOrderById_mapsANotFoundServiceErrorTo404() throws Exception {
        when(orderService.getById("nope")).thenThrow(new NotFoundException("Order not found"));

        mockMvc.perform(get("/api/order/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Order not found"));
    }

    @Test
    void getOrdersByDate_passesTheQueryParameterThrough() throws Exception {
        when(orderService.getOrdersByDate("2026-08-15")).thenReturn(List.of(dto()));

        mockMvc.perform(get("/api/order/date").param("date", "2026-08-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getOrdersByUserId_bindsThePathVariable() throws Exception {
        when(orderService.getOrdersByUserId(7L)).thenReturn(List.of(dto()));

        mockMvc.perform(get("/api/order/user/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(7));
    }

    @Test
    void createOrder_returns201AndForwardsTheBody() throws Exception {
        mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(dto())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Order created successfully"));

        ArgumentCaptor<OrderDto> captured = ArgumentCaptor.forClass(OrderDto.class);
        verify(orderService).createOrder(captured.capture());
        assertEquals("TCS", captured.getValue().getSymbol());
    }

    @Test
    void createOrder_rejectsAnInvalidBodyWith400() throws Exception {
        var invalid = dto();
        invalid.setSymbol("");
        invalid.setQuantity(0);

        mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_rejectsAMalformedBodyWith400() throws Exception {
        mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed or unreadable request body"));
    }

    @Test
    void updateOrder_overwritesTheBodyIdWithThePathId() throws Exception {
        // Otherwise a client could edit someone else's order by lying in the body.
        var body = dto();
        body.setId("spoofed");

        mockMvc.perform(put("/api/order/o1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        ArgumentCaptor<OrderDto> captured = ArgumentCaptor.forClass(OrderDto.class);
        verify(orderService).updateOrder(captured.capture());
        assertEquals("o1", captured.getValue().getId());
    }

    @Test
    void deleteOrder_returnsOkAndDelegates() throws Exception {
        mockMvc.perform(delete("/api/order/o1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Order deleted successfully"));

        verify(orderService).deleteOrder("o1");
    }

    @Test
    void deleteOrder_mapsANotFoundServiceErrorTo404() throws Exception {
        doThrow(new NotFoundException("Order not found")).when(orderService).deleteOrder("nope");

        mockMvc.perform(delete("/api/order/nope")).andExpect(status().isNotFound());
    }

    @Test
    void batchEndpointsTriggerTheirServiceCalls() throws Exception {
        mockMvc.perform(post("/api/order/initiate-mtf")).andExpect(status().isOk());
        mockMvc.perform(post("/api/order/update-status")).andExpect(status().isOk());
        mockMvc.perform(post("/api/order/start-trading")).andExpect(status().isOk());

        verify(mtfTradeEngine).initiateMtfOrders();
        verify(mtfTradeEngine).updateMtfOrderStatus();
        verify(mtfTradeEngine).startTrading();
    }
}
