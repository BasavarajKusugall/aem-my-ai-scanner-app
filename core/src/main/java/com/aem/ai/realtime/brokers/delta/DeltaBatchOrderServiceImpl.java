package com.aem.ai.realtime.brokers.delta;

import com.aem.ai.exception.ApiException;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Component(service = DeltaBatchOrderService.class, immediate = true)
public class DeltaBatchOrderServiceImpl implements DeltaBatchOrderService {

    private static final Logger LOG = LoggerFactory.getLogger(DeltaBatchOrderServiceImpl.class);

    @Reference private DeltaApiClient client;

    @Override
    public List<DeltaOrderResponse> placeBatchOrders(DeltaBatchCreateRequest req) throws ApiException {
        LOG.info("[DeltaBatch] placeBatchOrders count={}", req.orders != null ? req.orders.size() : 0);
        List<DeltaOrderResponse[]> res = client.post("/orders/batch", req, DeltaOrderResponse[].class);
        if (res == null || res.isEmpty()) throw new ApiException("No response from Delta");
        return Arrays.asList(res.get(0));
    }

    @Override
    public List<DeltaOrderResponse> editBatchOrders(DeltaBatchEditRequest req) throws ApiException {
        LOG.info("[DeltaBatch] editBatchOrders count={}", req.orders != null ? req.orders.size() : 0);
        List<DeltaOrderResponse[]> res = client.put("/orders/batch", req, DeltaOrderResponse[].class);
        if (res == null || res.isEmpty()) throw new ApiException("No response from Delta");
        return Arrays.asList(res.get(0));
    }

    @Override
    public List<DeltaOrderResponse> deleteBatchOrders(DeltaBatchDeleteRequest req) throws ApiException {
        LOG.info("[DeltaBatch] deleteBatchOrders count={}", req.orders != null ? req.orders.size() : 0);
        List<DeltaOrderResponse[]> res = client.delete("/orders/batch", req, DeltaOrderResponse[].class);
        if (res == null || res.isEmpty()) throw new ApiException("No response from Delta");
        return Arrays.asList(res.get(0));
    }
}