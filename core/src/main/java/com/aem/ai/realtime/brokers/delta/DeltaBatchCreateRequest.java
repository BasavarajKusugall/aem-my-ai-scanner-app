package com.aem.ai.realtime.brokers.delta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// Batch DTOs
@JsonIgnoreProperties(ignoreUnknown = true)
class DeltaBatchCreateRequest {
    public Integer product_id;
    public String product_symbol;
    public List<DeltaBatchOrderLine> orders;
}
