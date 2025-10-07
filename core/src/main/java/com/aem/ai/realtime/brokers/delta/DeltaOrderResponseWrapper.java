package com.aem.ai.realtime.brokers.delta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class DeltaOrderResponseWrapper {
    public boolean success;
    public DeltaOrderResponse result;
}
