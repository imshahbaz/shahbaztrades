package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.entity.Margin;

import java.util.Collection;
import java.util.Map;

public interface MarginService {

    Map<String, Margin> getMarginCache();

    void refreshMargins();

    Collection<Margin> getAllMargins();

    Margin getMargin(String symbol);

    void syncMTF(byte[] fileBytes);

    void syncAngelOneToken();
}
