package com.aem.ai.realtime.brokers.delta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class DeltaBatchEditRequest {
    public Integer product_id;
    public String product_symbol;
    public List<DeltaBatchEditLine> orders;
}
