package com.aem.ai.realtime.brokers.kite;

import com.aem.ai.pm.dto.BrokerAccountRef;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

// DTOs for requests/responses
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiWrapper<T> {
    public String status;
    public T data;
    public BrokerAccountRef brokerAccountRef;
    public Map<String,Object> error;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public BrokerAccountRef getBrokerAccountRef() {
        return brokerAccountRef;
    }

    public void setBrokerAccountRef(BrokerAccountRef brokerAccountRef) {
        this.brokerAccountRef = brokerAccountRef;
    }

    public Map<String, Object> getError() {
        return error;
    }

    public void setError(Map<String, Object> error) {
        this.error = error;
    }
}
