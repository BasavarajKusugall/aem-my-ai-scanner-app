package com.aem.ai.realtime.brokers.delta;

import com.aem.ai.exception.ApiException;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Component(service = DeltaPositionService.class, immediate = true)
public class DeltaPositionServiceImpl implements DeltaPositionService {

    private static final Logger LOG = LoggerFactory.getLogger(DeltaPositionServiceImpl.class);

    @Reference private DeltaApiClient client;

    @Override
    public List<DeltaPositionDetail> getPositions(Map<String,String> filters) throws ApiException {
        StringBuilder path = new StringBuilder("/positions");
        if (filters != null && !filters.isEmpty()) {
            path.append("?");
            List<String> q = new ArrayList<>();
            filters.forEach((k,v) -> q.add(k + "=" + v));
            path.append(String.join("&", q));
        }
        List<DeltaPositionDetail[]> res = client.get(path.toString(), DeltaPositionDetail[].class);
        if (res == null || res.isEmpty()) throw new ApiException("No response from Delta");
        return Arrays.asList(res.get(0));
    }

    @Override
    public DeltaPositionDetail changeMargin(int productId, String deltaMargin) throws ApiException {
        Map<String,Object> body = new HashMap<>();
        body.put("product_id", productId);
        body.put("delta_margin", deltaMargin);
        List<DeltaPositionDetail[]> res = client.post("/positions/change_margin", body, DeltaPositionDetail[].class);
        if (res == null || res.isEmpty()) throw new ApiException("No response from Delta");
        return res.get(0)[0];
    }

    @Override
    public boolean closeAllPositions(boolean closeAllPortfolio, boolean closeAllIsolated, long userId) throws ApiException {
        Map<String,Object> body = new HashMap<>();
        body.put("close_all_portfolio", closeAllPortfolio);
        body.put("close_all_isolated", closeAllIsolated);
        body.put("user_id", userId);
        List<Map> res = client.post("/positions/close_all", body, Map.class);
        return res != null && !res.isEmpty();
    }
}