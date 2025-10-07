package com.aem.ai.realtime.brokers.delta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Fill wrapper (simplified)
@JsonIgnoreProperties(ignoreUnknown = true)
class ApiListWrapper<T> {
    public boolean success;
    public T[] result;
}
