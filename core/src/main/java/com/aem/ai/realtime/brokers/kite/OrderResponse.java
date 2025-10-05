package com.aem.ai.realtime.brokers.kite;

import com.aem.ai.pm.dto.BrokerAccountRef;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderResponse {

    public BrokerAccountRef brokerAccountRef;
    private String status;

    public BrokerAccountRef getBrokerAccountRef() {
        return brokerAccountRef;
    }

    public void setBrokerAccountRef(BrokerAccountRef brokerAccountRef) {
        this.brokerAccountRef = brokerAccountRef;
    }

    // wrapper from API is { "status":"success", "data": { "order_id": "..." } }
    public String order_id;

    public OrderResponse(String orderId) {
        this.order_id = orderId;
    }

    public String getOrder_id() {
        return order_id;
    }

    public void setOrder_id(String order_id) {
        this.order_id = order_id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    // more fields may exist depending on the endpoint
}
