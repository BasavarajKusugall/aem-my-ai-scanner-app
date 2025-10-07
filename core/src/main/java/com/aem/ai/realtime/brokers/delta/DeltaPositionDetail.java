package com.aem.ai.realtime.brokers.delta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class DeltaPositionDetail {
    public Long user_id;
    public Integer size;
    public String entry_price;
    public String margin;
    public String product_symbol;
    public Integer product_id;
}
