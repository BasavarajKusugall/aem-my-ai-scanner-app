package com.aem.ai.realtime.brokers.delta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Position DTOs
@JsonIgnoreProperties(ignoreUnknown = true)
class DeltaPositionChangeMarginRequest {
    public Integer product_id;
    public String delta_margin;
}
