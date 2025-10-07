package com.aem.ai.realtime.brokers.delta;

import com.aem.ai.exception.ApiException;

import java.util.List;
import java.util.Map;

public interface DeltaPositionService {
    List<DeltaPositionDetail> getPositions(Map<String,String> filters) throws ApiException;
    DeltaPositionDetail changeMargin(int productId, String deltaMargin) throws ApiException;
    boolean closeAllPositions(boolean closeAllPortfolio, boolean closeAllIsolated, long userId) throws ApiException;
}