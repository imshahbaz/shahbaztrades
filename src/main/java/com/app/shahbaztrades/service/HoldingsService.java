package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.holdings.HoldingDto;
import com.app.shahbaztrades.model.enums.BrokerType;

import java.util.List;

public interface HoldingsService {

    String HOLDING_KEY = "HOLDINGS:";

    List<HoldingDto> getAllHoldings(BrokerType brokerType, UserDto userDto);

    boolean createHoldings(BrokerType brokerType, UserDto userDto, HoldingDto holdingDto);

    boolean deleteHoldings(BrokerType brokerType, UserDto userDto, String symbol);

    boolean updateHoldings(BrokerType brokerType, UserDto userDto, HoldingDto holdingDto);

    boolean deleteHoldingDetail(BrokerType brokerType, UserDto userDto, String symbol, int id);

    void updatePortfolio();
}
