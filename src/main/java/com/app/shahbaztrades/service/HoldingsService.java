package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.holdings.HoldingDto;
import com.app.shahbaztrades.model.enums.BrokerType;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface HoldingsService {

    String HOLDING_KEY = "HOLDINGS:";

    ResponseEntity<ApiResponse<List<HoldingDto>>> getAllHoldings(BrokerType brokerType, UserDto userDto);

    ResponseEntity<ApiResponse<Boolean>> createHoldings(BrokerType brokerType, UserDto userDto, HoldingDto holdingDto);

    ResponseEntity<ApiResponse<Boolean>> deleteHoldings(BrokerType brokerType, UserDto userDto, String symbol);

    ResponseEntity<ApiResponse<Boolean>> updateHoldings(BrokerType brokerType, UserDto userDto, HoldingDto holdingDto);

    ResponseEntity<ApiResponse<Boolean>> deleteHoldingDetail(BrokerType brokerType, UserDto userDto, String symbol, int id);

    void updatePortfolio();
}
