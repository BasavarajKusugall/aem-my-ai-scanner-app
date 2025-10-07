package com.aem.ai.realtime.brokers.delta;

import com.aem.ai.realtime.brokers.kite.OrderRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class DeltaEditOrderRequest {
    public Long id;
    public Integer product_id;
    public String product_symbol;
    public String limit_price;
    public Integer size;

    public static DeltaEditOrderRequest fromGeneric(String orderId, OrderRequest r) {
        DeltaEditOrderRequest e = new DeltaEditOrderRequest();
        e.id = Long.parseLong(orderId);
        e.product_symbol = r.tradingsymbol;
        e.size = r.quantity != null ? r.quantity.intValue() : null;
        e.limit_price = r.price != null ? String.valueOf(r.price) : null;
        return e;
    }
}
