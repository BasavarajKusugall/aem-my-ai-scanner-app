package com.aem.ai.realtime.brokers.delta;

import com.aem.ai.exception.ApiException;

import java.util.List;

public interface DeltaBatchOrderService {
    List<DeltaOrderResponse> placeBatchOrders(DeltaBatchCreateRequest req) throws ApiException;
    List<DeltaOrderResponse> editBatchOrders(DeltaBatchEditRequest req) throws ApiException;
    List<DeltaOrderResponse> deleteBatchOrders(DeltaBatchDeleteRequest req) throws ApiException;
}

