package com.app.shahbaztrades.service;

/** Scores strategies against their own recent signals, to keep their published success rate honest. */
public interface StrategyBacktestService {

    void updateStrategyBacktestData();
}
