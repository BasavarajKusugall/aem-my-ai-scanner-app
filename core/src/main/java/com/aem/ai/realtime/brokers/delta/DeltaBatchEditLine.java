package com.aem.ai.realtime.brokers.delta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class DeltaBatchEditLine {
    public Long id;
    public String limit_price;
    public Integer size;
}
