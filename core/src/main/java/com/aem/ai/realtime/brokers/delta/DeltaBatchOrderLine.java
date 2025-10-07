package com.aem.ai.realtime.brokers.delta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class DeltaBatchOrderLine {
    public String limit_price;
    public Integer size;
    public String side;
    public String order_type;
    public String client_order_id;
}
