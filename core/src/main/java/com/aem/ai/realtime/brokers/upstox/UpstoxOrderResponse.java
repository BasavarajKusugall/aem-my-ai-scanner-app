package com.aem.ai.realtime.brokers.upstox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class UpstoxOrderResponse {
    private String order_id;
    private String status;

    public String getOrderId() { return order_id; }
    public void setOrderId(String order_id) { this.order_id = order_id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}